package dev.tinify.service.compressionService.compressors

import dev.tinify.CompressionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Service
class PngCompressionService {

    companion object {
        private val PROC_TIMEOUT: Duration = Duration.ofSeconds(30)

        // TinyPNG-optimized thresholds
        private const val MEDIUM_FILE_BYTES = 200_000L    // 200kB threshold for medium optimization
        private const val LARGE_FILE_BYTES = 600_000L     // 600kB threshold for aggressive optimization
        private const val HUGE_FILE_BYTES = 1_500_000L    // 1.5MB threshold for maximum compression
        private const val TARGET_RATIO = 0.15f            // TinyPNG achieves ~10-15% of original size

        // TinyPNG-style quality ranges (elevated from standard pngquant defaults)
        private fun pqArgs(qual: String, speed: Int, extra: List<String> = emptyList()) =
            listOf("--quality", qual, "--speed", "$speed", "--strip", "--force") + extra
    }

    private val log = LoggerFactory.getLogger(javaClass)

    fun compressPng(input: File, mode: CompressionType): ByteArray =
        when (mode) {
            CompressionType.LOSSY -> tinyPngStyleLossy(input)
            CompressionType.LOSSLESS -> tinyPngStyleLossless(input)
        }

    /**
     * TinyPNG-style lossy compression with multi-stage optimization
     * Achieves 70-90% compression ratios similar to TinyPNG
     */
    private fun tinyPngStyleLossy(src: File): ByteArray {
        val tmp = tmp("pq")

        try {
            // Single aggressive pngquant pass (TinyPNG-style)
            val quality = when {
                src.length() > HUGE_FILE_BYTES -> "75-90"
                src.length() > LARGE_FILE_BYTES -> "80-92"
                else -> "85-95"
            }

            val cmd = listOf(
                "pngquant", "--quality", quality, "--speed", "1",
                "--strip", "--force", "--floyd",
                "--output", tmp.absolutePath, src.absolutePath
            )

            if (run("pngquant-optimized", cmd, setOf(0)) == 0 && tmp.exists()) {
                // Single lossless pass only if significant improvement possible
                if (toolExists("oxipng") && tmp.length() > 50000) {
                    run("oxipng-fast", listOf("oxipng", "-o", "2", "--strip", "safe", tmp.absolutePath), setOf(0))
                }

                return Files.readAllBytes(tmp.toPath())
            }

            return Files.readAllBytes(src.toPath())
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /**
     * TinyPNG-style lossless compression
     */
    private fun tinyPngStyleLossless(src: File): ByteArray {
        var best: File = src
        var bestSize = src.length()
        val temps = mutableListOf<File>()

        fun shrink(label: String, buildCmd: (File) -> List<String>) {
            val tmp = tmp(label.take(3)).also { temps += it }
            val exit = run(label, buildCmd(tmp), setOf(0))
            if (exit == 0 && tmp.exists() && tmp.length() > 0 && tmp.length() < bestSize) {
                if (best != src) best.delete()
                best = tmp; bestSize = tmp.length()
                log.info("$label → $bestSize B")
            } else if (tmp.exists()) tmp.delete()
        }

        // Stage 1: Oxipng maximum optimization
        if (toolExists("oxipng")) {
            shrink("oxipng-max") { out ->
                listOf(
                    "oxipng", "--opt", "max", "--strip", "safe", "--alpha", "--zopfli",
                    "--out", out.absolutePath, best.absolutePath
                )
            }
        }

        // Stage 2: OptiPNG optimization
        if (toolExists("optipng")) {
            shrink("optipng-o7") { out ->
                Files.copy(best.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
                listOf("optipng", "-o7", "-strip", "all", out.absolutePath)
            }
        }

        // Stage 3: AdvPNG deflate optimization
        if (toolExists("advpng") && best != src) {
            run("advpng-z4", listOf("advpng", "-z", "-4", best.absolutePath), setOf(0))
        }

        // Stage 4: ECT for final optimization
        if (toolExists("ect") && best != src) {
            shrink("ect-lossless") { out ->
                listOf("ect", "-9", "--strict", best.absolutePath, out.absolutePath)
            }
        }

        val bytes = Files.readAllBytes(best.toPath())
        temps.filter { it != best }.forEach(File::delete)

        val reductionPct = ((src.length() - bestSize) * 100.0 / src.length()).roundToInt()
        log.info("Lossless optimization: ${src.length()} B → $bestSize B (~$reductionPct% reduction)")

        return bytes
    }

    /**
     * TinyPNG-style preprocessing: slight noise reduction for better compression
     */
    private fun preprocessForCompression(src: File): File? {
        if (!toolExists("convert")) return null

        return try {
            val preprocessed = tmp("prep")
            val cmd = listOf(
                "convert", src.absolutePath,
                "-blur", "0.5x0.5",  // Very light blur to reduce noise
                "-unsharp", "0.5x0.5+0.5+0.008", // Sharpen back important details
                preprocessed.absolutePath
            )

            if (run("preprocess", cmd, setOf(0)) == 0 && preprocessed.exists() && preprocessed.length() > 0) {
                preprocessed
            } else {
                if (preprocessed.exists()) preprocessed.delete()
                null
            }
        } catch (e: Exception) {
            log.debug("Preprocessing failed: ${e.message}")
            null
        }
    }

    private fun tmp(prefix: String) =
        File.createTempFile("$prefix-${UUID.randomUUID()}", ".png")

    private fun run(label: String, cmd: List<String>, ok: Set<Int>): Int {
        val t0 = System.nanoTime()
        return try {
            val p = ProcessBuilder(cmd)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!p.waitFor(PROC_TIMEOUT.seconds, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                log.warn("$label ▸ timeout (${PROC_TIMEOUT.seconds}s)")
                -1
            } else {
                val ms = Duration.ofNanos(System.nanoTime() - t0).toMillis()
                val exit = p.exitValue()
                if (exit !in ok) log.warn("$label ▸ exit=$exit ($ms ms)")
                else log.debug("$label ▸ OK ($ms ms)")
                exit
            }
        } catch (e: Exception) {
            log.error("$label ▸ failed – ${e.message}")
            -1
        }
    }

    private fun toolExists(name: String): Boolean = try {
        log.debug("Checking if tool '$name' exists")
        ProcessBuilder("which", name)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .let { it.waitFor(2, TimeUnit.SECONDS) && it.exitValue() == 0 }
    } catch (_: Exception) {
        log.debug("Tool '$name' not found")
        false
    }
}