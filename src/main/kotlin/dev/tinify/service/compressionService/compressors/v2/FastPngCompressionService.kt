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
        log.info("PNG File input: ${input.length()} bytes, read as: ${inputBytes.size} bytes")
        return compressPngFromBytes(inputBytes, mode)
    }

    fun compressPngFromBytes(inputBytes: ByteArray, mode: CompressionType): ByteArray {
        val startTime = System.currentTimeMillis()
        val originalSize = inputBytes.size

        log.info("PNG compression starting - Original size: ${originalSize} bytes, Mode: ${mode}")

        return try {
            // Try multiple compression strategies and pick the best result
            val result = when (mode) {
                CompressionType.LOSSY -> findBestLossyCompression(inputBytes)
                CompressionType.LOSSLESS -> findBestLosslessCompression(inputBytes)
            }

            val duration = System.currentTimeMillis() - startTime

            log.info("PNG compression result - Original: ${originalSize} B, Compressed: ${result.size} B, Ratio: ${(result.size.toDouble() / originalSize * 100).toInt()}%")

            // CRITICAL: Always return original if compressed is not smaller
            if (result.size >= originalSize) {
                log.warn("PNG compressed size (${result.size} B) >= original size (${originalSize} B), returning original")
                return inputBytes
            }

            log.info("Fast PNG compression: ${originalSize} B → ${result.size} B (${duration}ms)")
            result
        } catch (e: Exception) {
            log.error("Fast PNG compression failed, using original: ${e.message}")
            inputBytes
        }
    }

    private fun findBestLossyCompression(inputBytes: ByteArray): ByteArray {
        log.debug("Starting lossy compression attempts for ${inputBytes.size} bytes")

        val compressionAttempts = listOf(
            // TinyPNG-like settings (most aggressive first)
            Triple("60-75", "1", true),   // More aggressive, best quality
            Triple("65-80", "1", true),   // Original setting
            Triple("70-85", "1", true),   // Less aggressive fallback
            Triple("50-70", "2", false),  // Faster alternative
        )

        var bestResult = inputBytes
        var bestSize = inputBytes.size

        for ((quality, speed, useOptipng) in compressionAttempts) {
            try {
                log.debug("Attempting PNG compression with quality=$quality, speed=$speed, optipng=$useOptipng")
                val compressed = compressWithPngquant(inputBytes, quality, speed)
                log.debug("pngquant result: ${inputBytes.size} B → ${compressed.size} B")

                val finalResult = if (useOptipng && compressed.size < bestSize) {
                    log.debug("Applying OptiPNG post-processing")
                    // Apply OptiPNG for additional compression
                    val optipngResult = applyOptipng(compressed)
                    if (optipngResult != null) {
                        log.debug("OptiPNG result: ${compressed.size} B → ${optipngResult.size} B")
                        optipngResult
                    } else {
                        compressed
                    }
                } else {
                    compressed
                }

                if (finalResult.size < bestSize) {
                    bestResult = finalResult
                    bestSize = finalResult.size
                    log.info("New best PNG compression: ${bestSize} bytes with quality=$quality, speed=$speed, optipng=$useOptipng")
                }
            } catch (e: Exception) {
                log.debug("PNG compression attempt failed with quality=$quality: ${e.message}")
                continue
            }
        }

        log.info("Best lossy compression: ${inputBytes.size} B → ${bestResult.size} B")
        return bestResult
    }

    private fun findBestLosslessCompression(inputBytes: ByteArray): ByteArray {
        log.debug("Starting lossless compression attempts for ${inputBytes.size} bytes")

        // For lossless, we still use pngquant but with high quality settings
        val attempts = listOf(
            Triple("90-100", "1", true),  // High quality + OptiPNG
            Triple("85-95", "1", true),   // Slightly lower + OptiPNG
            Triple("90-100", "2", false), // High quality, faster
        )

        var bestResult = inputBytes
        var bestSize = inputBytes.size

        for ((quality, speed, useOptipng) in attempts) {
            try {
                log.debug("Attempting PNG lossless compression with quality=$quality, speed=$speed, optipng=$useOptipng")
                val compressed = compressWithPngquant(inputBytes, quality, speed)
                log.debug("pngquant lossless result: ${inputBytes.size} B → ${compressed.size} B")

                val finalResult = if (useOptipng && compressed.size < bestSize) {
                    log.debug("Applying OptiPNG post-processing for lossless")
                    val optipngResult = applyOptipng(compressed)
                    if (optipngResult != null) {
                        log.debug("OptiPNG lossless result: ${compressed.size} B → ${optipngResult.size} B")
                        optipngResult
                    } else {
                        compressed
                    }
                } else {
                    compressed
                }

                if (finalResult.size < bestSize) {
                    bestResult = finalResult
                    bestSize = finalResult.size
                    log.info("New best PNG lossless compression: ${bestSize} bytes with quality=$quality, speed=$speed, optipng=$useOptipng")
                }
            } catch (e: Exception) {
                log.debug("PNG lossless compression attempt failed: ${e.message}")
                continue
            }
        }

        // If pngquant doesn't help, try OptiPNG alone
        if (bestResult.size >= inputBytes.size) {
            log.debug("pngquant didn't improve compression, trying OptiPNG alone")
            val optipngOnly = applyOptipng(inputBytes)
            if (optipngOnly != null && optipngOnly.size < inputBytes.size) {
                log.info("OptiPNG-only improved compression: ${inputBytes.size} B → ${optipngOnly.size} B")
                bestResult = optipngOnly
            }
        }

        log.info("Best lossless compression: ${inputBytes.size} B → ${bestResult.size} B")
        return bestResult
    }

    private fun compressWithPngquant(inputBytes: ByteArray, quality: String, speed: String): ByteArray {
        val command = listOf(
            "pngquant",
            "--force",
            "--strip",
            "--quality", quality,
            "--speed", speed,
            "-"
        )

        return executeCompressionCommand(command, inputBytes, "pngquant")
    }

    private fun applyOptipng(inputBytes: ByteArray): ByteArray? {
        return try {
            val command = listOf(
                "optipng",
                "-o7",      // Maximum optimization
                "-strip",   // Remove metadata
                "-stdout",  // Output to stdout
                "-"         // Read from stdin
            )

            executeCompressionCommand(command, inputBytes, "optipng")
        } catch (e: Exception) {
            log.debug("OptiPNG compression failed: ${e.message}")
            null
        }
    }

    private fun executeCompressionCommand(command: List<String>, inputBytes: ByteArray, toolName: String): ByteArray {
        log.debug("Executing $toolName command: ${command.joinToString(" ")} (input: ${inputBytes.size} bytes)")

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
            throw RuntimeException("$toolName process timeout")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            val errorMsg = process.errorStream.bufferedReader().readText()
            if (exitCode == 99 && toolName == "pngquant") {
                // Quality too low, this is expected for some images
                log.debug("$toolName quality threshold not met (exit code 99)")
                throw RuntimeException("Quality threshold not met")
            }
            throw RuntimeException("$toolName failed with exit code $exitCode: $errorMsg")
        }

        log.debug("$toolName completed: ${inputBytes.size} B → ${compressedBytes.size} B")
        return compressedBytes
    }
}