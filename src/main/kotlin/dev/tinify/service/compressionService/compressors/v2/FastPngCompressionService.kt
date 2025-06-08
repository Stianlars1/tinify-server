package dev.tinify.service.compressionService.compressors.v2

import dev.tinify.CompressionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.TimeUnit

@Service
class FastPngCompressionService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun compressPng(input: File, mode: CompressionType): ByteArray {
        val inputBytes = input.readBytes()
        return compressPngFromBytes(inputBytes, mode)
    }

    fun compressPngFromBytes(inputBytes: ByteArray, mode: CompressionType): ByteArray {
        val startTime = System.currentTimeMillis()

        return try {
            // Use pngquant with optimized settings that match TinyPNG
            val result = when (mode) {
                CompressionType.LOSSY -> compressWithPngquant(inputBytes, true)
                CompressionType.LOSSLESS -> compressWithPngquant(inputBytes, false)
            }

            val duration = System.currentTimeMillis() - startTime
            log.info("Fast PNG compression: ${inputBytes.size} B → ${result.size} B (${duration}ms)")

            result
        } catch (e: Exception) {
            log.error("Fast PNG compression failed, using original: ${e.message}")
            inputBytes
        }
    }

    private fun compressWithPngquant(inputBytes: ByteArray, lossy: Boolean): ByteArray {
        // Build pngquant command with TinyPNG-equivalent settings
        val command = buildList {
            add("pngquant")
            add("--force") // Overwrite existing files
            add("--strip") // Remove metadata like TinyPNG does

            if (lossy) {
                // TinyPNG-equivalent lossy settings
                add("--quality")
                add("65-80") // Sweet spot for TinyPNG-like quality
                add("--speed")
                add("1") // Best quality (slower but matches TinyPNG)
            } else {
                // Lossless settings - still quantize but with higher quality
                add("--quality")
                add("90-100")
                add("--speed")
                add("1")
            }

            add("-") // Read from stdin
        }

        log.debug("Executing pngquant command: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        // Write input bytes to stdin
        process.outputStream.use { outputStream ->
            outputStream.write(inputBytes)
            outputStream.flush()
        }

        // Read compressed result from stdout
        val compressedBytes = process.inputStream.use { inputStream ->
            inputStream.readAllBytes()
        }

        // Wait for process completion with timeout
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw RuntimeException("pngquant process timeout")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            val errorMsg = process.errorStream.bufferedReader().readText()
            log.warn("pngquant exited with code $exitCode: $errorMsg")

            // If pngquant fails (e.g., quality too low), apply fallback compression
            if (exitCode == 99) { // pngquant quality too low
                return applyFallbackCompression(inputBytes, lossy)
            }
            throw RuntimeException("pngquant failed with exit code $exitCode: $errorMsg")
        }

        return compressedBytes
    }

    private fun applyFallbackCompression(inputBytes: ByteArray, lossy: Boolean): ByteArray {
        log.info("Applying fallback compression")

        // Fallback: Use pngquant with more aggressive settings
        val command = listOf(
            "pngquant",
            "--force",
            "--strip",
            if (lossy) "--quality=50-75" else "--quality=80-95",
            "--speed", "3", // Faster speed for fallback
            "-"
        )

        val process = ProcessBuilder(command).start()

        process.outputStream.use { it.write(inputBytes) }
        val result = process.inputStream.readAllBytes()

        if (process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0) {
            return result
        }

        // Final fallback: return original
        log.warn("Fallback compression also failed, returning original")
        return inputBytes
    }
}