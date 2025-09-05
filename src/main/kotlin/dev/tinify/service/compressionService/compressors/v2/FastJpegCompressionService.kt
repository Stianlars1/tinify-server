package dev.tinify.service.compressionService.compressors.v2

import dev.tinify.CompressionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

@Service
class FastJpegCompressionService {

    private val log = LoggerFactory.getLogger(javaClass)

    // Prefer mozjpeg under /opt; otherwise rely on PATH
    private val cjpegBin = preferred("/opt/mozjpeg/bin/cjpeg", "cjpeg")
    private val djpegBin = preferred("/opt/mozjpeg/bin/djpeg", "djpeg")
    private val jpegtranBin = preferred("/opt/mozjpeg/bin/jpegtran", "jpegtran")
    private val jpegoptimBin = preferred("jpegoptim")

    private fun preferred(vararg candidates: String): String =
        candidates.firstOrNull { it.startsWith("/") && File(it).canExecute() } ?: candidates.first()

    private fun isProbablyJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
                bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
                bytes[bytes.lastIndex - 1] == 0xFF.toByte() && bytes[bytes.lastIndex] == 0xD9.toByte()

    private fun isDecodableImage(bytes: ByteArray): Boolean =
        try {
            ImageIO.read(ByteArrayInputStream(bytes)) != null
        } catch (_: Exception) {
            false
        }

    private companion object {
        private const val TIMEOUT_SECONDS = 30L
        private const val MIN_VALID_JPEG_BYTES = 256 // guard against tiny/empty "JPEGs"
        private val LOSSY_QUALITIES = listOf(85, 80, 75, 70)
        private const val TINY_INPUT_THRESHOLD = 10_000 // ~10 KB: try baseline as well
    }

    fun compressJpegInMemory(input: File, mode: CompressionType): ByteArray {
        return compressJpegFromBytes(input.readBytes(), mode)
    }

    fun compressJpegFromBytes(inputBytes: ByteArray, mode: CompressionType): ByteArray {
        val start = System.currentTimeMillis()
        val originalSize = inputBytes.size

        fun safeReturn(reason: String): ByteArray {
            log.warn("Returning original JPEG ($originalSize B). Reason: $reason")
            return inputBytes
        }

        return try {
            val result = when (mode) {
                CompressionType.LOSSY -> findBestLossyCompression(inputBytes)
                CompressionType.LOSSLESS -> findBestLosslessCompression(inputBytes)
            }

            val ms = System.currentTimeMillis() - start

            // Strong validation
            if (result.isEmpty()) return safeReturn("empty result from pipeline")
            if (result.size < MIN_VALID_JPEG_BYTES) return safeReturn("too small result (${result.size} B)")
            if (!isProbablyJpeg(result)) return safeReturn("missing JPEG SOI/EOI markers")
            if (!isDecodableImage(result)) return safeReturn("undecodable JPEG output")
            if (result.size >= originalSize) return safeReturn("not smaller (${result.size} B >= $originalSize B)")

            log.info("Fast JPEG compression: $originalSize B → ${result.size} B (${ms}ms)")
            result
        } catch (e: Exception) {
            log.error("Fast JPEG compression failed: ${e.message}")
            inputBytes
        }
    }

    /** ---------- LOSSY PIPELINE (robust, single djpeg) ---------- */
    private fun findBestLossyCompression(inputBytes: ByteArray): ByteArray {
        var best = inputBytes
        var bestSize = inputBytes.size

        val ppm: ByteArray = try {
            // JPEG → PPM on stdout. -rgb ensures sane color for odd sources (e.g. CMYK)
            decodeWithDjpeg(inputBytes)
        } catch (e: Exception) {
            log.debug("djpeg failed (${e.message}). Using fallbacks.")
            return fallbackLossyWhenDjpegMissing(inputBytes)
        }

        val tiny = inputBytes.size < TINY_INPUT_THRESHOLD

        for (q in LOSSY_QUALITIES) {
            try {
                for (variant in encodeWithCjpegVariants(ppm, q, tiny)) {
                    val candidate = applyJpegoptim(variant) ?: variant
                    if (isCandidateBetter(candidate, bestSize)) {
                        best = candidate
                        bestSize = candidate.size
                        log.debug("Lossy q=$q ${if (tiny) "(prog/baseline)" else "(prog)"} → $bestSize bytes")
                    }
                }
            } catch (e: Exception) {
                log.debug("Lossy attempt q=$q failed: ${e.message}")
            }
        }

        if (bestSize >= inputBytes.size) {
            // Robust fallback via ImageMagick
            fallbackWithImageMagickJpeg(inputBytes, quality = 80)?.let { magickBytes ->
                if (isCandidateBetter(magickBytes, bestSize)) return magickBytes
            }
            // Pure Java fallback (no external deps)
            fallbackWithImageIO(inputBytes, quality = 0.8f)?.let { javaBytes ->
                if (isCandidateBetter(javaBytes, bestSize)) return javaBytes
            }
        }

        return best
    }

    private fun fallbackLossyWhenDjpegMissing(inputBytes: ByteArray): ByteArray {
        fallbackWithImageMagickJpeg(inputBytes, quality = 80)?.let { return it }
        fallbackWithImageIO(inputBytes, quality = 0.8f)?.let { return it }
        return inputBytes
    }

    /**
     * Yields cjpeg outputs for (a) progressive, and for tiny inputs also (b) baseline.
     * Progressive is generally better for larger images; baseline can win on tiny icons.
     */
    private fun encodeWithCjpegVariants(ppmBytes: ByteArray, quality: Int, tiny: Boolean): Sequence<ByteArray> =
        sequence {
            // Progressive
            yield(encodeWithCjpeg(ppmBytes, quality, progressive = true))
            // Baseline for tiny inputs
            if (tiny) yield(encodeWithCjpeg(ppmBytes, quality, progressive = false))
        }

    /** ---------- LOSSLESS PIPELINE ---------- */
    private fun findBestLosslessCompression(inputBytes: ByteArray): ByteArray {
        var best = inputBytes
        var bestSize = inputBytes.size

        try {
            // jpegtran is lossless structural optimization; then jpegoptim may shave a bit more
            val jt = compressWithJpegtran(inputBytes)
            val maybeOptimized = applyJpegoptim(jt) ?: jt
            if (isCandidateBetter(maybeOptimized, bestSize)) {
                best = maybeOptimized
                bestSize = best.size
                log.debug("Lossless jpegtran(+jpegoptim) → $bestSize bytes")
            }
        } catch (e: Exception) {
            log.debug("Lossless pipeline failed: ${e.message}")
        }

        return best
    }

    /** Compare candidate results: must be non-empty, valid JPEG, decodable, and smaller. */
    private fun isCandidateBetter(candidate: ByteArray, currentBestSize: Int): Boolean {
        if (candidate.isEmpty() || candidate.size < MIN_VALID_JPEG_BYTES) return false
        if (!isProbablyJpeg(candidate) || !isDecodableImage(candidate)) return false
        return candidate.size < currentBestSize
    }

    /** ---------- External tools (streaming, no temp files) ---------- */

    private fun decodeWithDjpeg(inputJpeg: ByteArray): ByteArray {
        val cmd = listOf(djpegBin, "-ppm", "-rgb", "-outfile", "/dev/stdout", "/dev/stdin")
        return executeImageCommand(cmd, inputJpeg, "djpeg", mustBeNonEmpty = true)
    }

    private fun encodeWithCjpeg(ppmBytes: ByteArray, quality: Int, progressive: Boolean): ByteArray {
        val args = mutableListOf(
            cjpegBin,
            "-quality", quality.toString(),
            "-optimize",
            "-outfile", "/dev/stdout",
            "/dev/stdin"
        )
        if (progressive) args.add(2, "-progressive") // insert after -quality to keep readable logs
        return executeImageCommand(
            args,
            ppmBytes,
            "cjpeg${if (progressive) "(prog)" else "(base)"}",
            mustBeNonEmpty = true
        )
    }

    private fun compressWithJpegtran(jpegBytes: ByteArray): ByteArray {
        val cmd = listOf(
            jpegtranBin,
            "-copy", "none",
            "-optimize",
            "-progressive",
            "-outfile", "/dev/stdout",
            "/dev/stdin"
        )
        return executeImageCommand(cmd, jpegBytes, "jpegtran", mustBeNonEmpty = true)
    }

    private fun applyJpegoptim(jpegBytes: ByteArray): ByteArray? = try {
        val cmd = listOf(
            jpegoptimBin,
            "--stdin", "--stdout",
            "--strip-all",
            "--all-progressive"
        )
        executeImageCommand(cmd, jpegBytes, "jpegoptim", mustBeNonEmpty = false).takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        log.debug("jpegoptim failed: ${e.message}")
        null
    }

    /** Fallback using ImageMagick (magick or convert). */
    private fun fallbackWithImageMagickJpeg(inputBytes: ByteArray, quality: Int = 80): ByteArray? {
        val candidates = listOf(
            listOf(
                "magick",
                "jpg:-",
                "-strip",
                "-interlace",
                "Plane",
                "-sampling-factor",
                "4:2:0",
                "-quality",
                quality.toString(),
                "jpg:-"
            ),
            listOf(
                "convert",
                "jpg:-",
                "-strip",
                "-interlace",
                "Plane",
                "-sampling-factor",
                "4:2:0",
                "-quality",
                quality.toString(),
                "jpg:-"
            )
        )
        for (cmd in candidates) {
            try {
                val out = executeImageCommand(cmd, inputBytes, cmd.first(), mustBeNonEmpty = true)
                if (out.isNotEmpty()) return out
            } catch (_: Exception) { /* try next */
            }
        }
        return null
    }

    /** Last-resort fallback: re-encode using pure Java (ImageIO). */
    private fun fallbackWithImageIO(data: ByteArray, quality: Float = 0.8f): ByteArray? {
        return try {
            val img = ImageIO.read(ByteArrayInputStream(data)) ?: return null
            val writers = ImageIO.getImageWritersByFormatName("jpeg")
            if (!writers.hasNext()) return null
            val writer = writers.next()
            val baos = ByteArrayOutputStream()
            writer.output = MemoryCacheImageOutputStream(baos)
            val params = writer.defaultWriteParam
            if (params.canWriteCompressed()) {
                params.compressionMode = ImageWriteParam.MODE_EXPLICIT
                params.compressionQuality = quality.coerceIn(0.05f, 1.0f)
                params.progressiveMode = ImageWriteParam.MODE_DEFAULT
            }
            writer.write(null, IIOImage(img, null, null), params)
            writer.dispose()
            baos.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun executeImageCommand(
        command: List<String>,
        inputBytes: ByteArray,
        toolName: String,
        mustBeNonEmpty: Boolean
    ): ByteArray {
        log.debug("Executing $toolName: ${command.joinToString(" ")}")

        val proc = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        // Feed stdin & close (signals EOF)
        proc.outputStream.use { it.write(inputBytes) }

        // Read stdout
        val stdout = proc.inputStream.readAllBytes()

        // Wait and collect stderr
        val finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val code = if (finished) proc.exitValue() else -1
        val stderr = proc.errorStream.bufferedReader().readText()

        if (!finished) {
            proc.destroyForcibly()
            throw RuntimeException("$toolName timeout after ${TIMEOUT_SECONDS}s")
        }
        if (code != 0) {
            throw RuntimeException("$toolName exit=$code: ${stderr.ifBlank { "(no stderr)" }}")
        }
        if (mustBeNonEmpty && stdout.isEmpty()) {
            log.warn("$toolName produced EMPTY output for ${inputBytes.size} B input. stderr: ${stderr.take(400)}")
            throw RuntimeException("$toolName produced empty output")
        }

        return stdout
    }
}
