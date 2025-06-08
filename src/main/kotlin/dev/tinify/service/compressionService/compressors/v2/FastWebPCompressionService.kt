package dev.tinify.service.compressionService.compressors.v2

import dev.tinify.CompressionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@Service
class FastWebPCompressionService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun compressWebPInMemory(input: File, mode: CompressionType): ByteArray {
        val inputBytes = input.readBytes()
        return compressWebPFromBytes(inputBytes, mode)
    }

    fun compressWebPFromBytes(inputBytes: ByteArray, mode: CompressionType): ByteArray {
        val startTime = System.currentTimeMillis()
        val originalSize = inputBytes.size

        return try {
            val result = when (mode) {
                CompressionType.LOSSY -> findBestLossyRecompression(inputBytes)
                CompressionType.LOSSLESS -> findBestLosslessCompression(inputBytes)
            }

            val duration = System.currentTimeMillis() - startTime

            // CRITICAL: Always return original if compressed is not smaller
            if (result.size >= originalSize) {
                log.info("Compressed WebP was not smaller (${result.size} B >= ${originalSize} B), returning original")
                return inputBytes
            }

            log.info("Fast WebP compression: ${originalSize} B → ${result.size} B (${duration}ms)")
            result
        } catch (e: Exception) {
            log.error("Fast WebP compression failed, using original: ${e.message}")
            inputBytes
        }
    }

    private fun findBestLossyRecompression(inputBytes: ByteArray): ByteArray {
        // ULTRA-AGGRESSIVE settings for WebP-to-WebP recompression!
        val compressionAttempts = listOf(
            // Target size approach - most effective for recompression
            Triple("size", (inputBytes.size * 0.7).toInt(), "Target 30% reduction"),
            Triple("size", (inputBytes.size * 0.6).toInt(), "Target 40% reduction"),
            Triple("size", (inputBytes.size * 0.5).toInt(), "Target 50% reduction"),

            // Ultra-aggressive quality settings for WebP recompression
            Triple("quality", 50, "Moderate quality"),
            Triple("quality", 40, "Low quality"),
            Triple("quality", 35, "Very low quality"),
            Triple("quality", 30, "Ultra low quality"),
        )

        var bestResult = inputBytes
        var bestSize = inputBytes.size

        for ((type, value, description) in compressionAttempts) {
            try {
                val compressed = when (type) {
                    "size" -> compressWithTargetSize(inputBytes, value)
                    "quality" -> compressWithAggressiveQuality(inputBytes, value)
                    else -> continue
                }

                if (compressed.size < bestSize) {
                    bestResult = compressed
                    bestSize = compressed.size
                    log.info("New best WebP recompression: ${bestSize} bytes using $description (${((bestSize.toDouble() / inputBytes.size) * 100).toInt()}% of original)")
                }
            } catch (e: Exception) {
                log.debug("WebP compression attempt failed with $description: ${e.message}")
                continue
            }
        }

        return bestResult
    }

    private fun findBestLosslessCompression(inputBytes: ByteArray): ByteArray {
        val attempts = listOf(
            Pair(9, 6),  // Maximum compression, best method
            Pair(6, 6),  // Balanced compression, best method
            Pair(9, 4),  // Maximum compression, faster method
        )

        var bestResult = inputBytes
        var bestSize = inputBytes.size

        for ((effort, method) in attempts) {
            try {
                val compressed = compressWithCwebpLossless(inputBytes, effort, method)

                if (compressed.size < bestSize) {
                    bestResult = compressed
                    bestSize = compressed.size
                    log.debug("New best WebP lossless compression: ${bestSize} bytes with effort=$effort, method=$method")
                }
            } catch (e: Exception) {
                log.debug("WebP lossless compression attempt failed: ${e.message}")
                continue
            }
        }

        return bestResult
    }

    private fun compressWithTargetSize(inputBytes: ByteArray, targetSize: Int): ByteArray {
        // Create temp files
        val inputFile = File.createTempFile("webp-input", ".webp")
        val outputFile = File.createTempFile("webp-output", ".webp")

        return try {
            // Write input to temp file
            Files.write(inputFile.toPath(), inputBytes)

            val command = listOf(
                "cwebp",
                "-size", targetSize.toString(),  // Target specific file size
                "-pass", "10",                   // Maximum passes for target size
                "-m", "6",                       // Best compression method
                "-quiet",
                inputFile.absolutePath,
                "-o", outputFile.absolutePath
            )

            log.debug("Executing target size compression: ${targetSize} bytes")
            val success = executeWebPCommand(command)

            if (success && outputFile.exists()) {
                Files.readAllBytes(outputFile.toPath())
            } else {
                throw RuntimeException("cwebp failed to create output file")
            }
        } finally {
            // Cleanup temp files
            inputFile.delete()
            outputFile.delete()
        }
    }

    private fun compressWithAggressiveQuality(inputBytes: ByteArray, quality: Int): ByteArray {
        // Create temp files
        val inputFile = File.createTempFile("webp-input", ".webp")
        val outputFile = File.createTempFile("webp-output", ".webp")

        return try {
            // Write input to temp file
            Files.write(inputFile.toPath(), inputBytes)

            val command = listOf(
                "cwebp",
                "-q", quality.toString(),       // FIXED: Use -q instead of -quality
                "-m", "6",                      // Best compression method
                "-pass", "6",                   // Multiple passes
                "-segments", "4",               // More segments for better compression
                "-sns", "80",                   // Spatial noise shaping
                "-f", "25",                     // Deblocking filter strength
                "-sharpness", "0",              // No sharpening (saves space)
                "-quiet",
                inputFile.absolutePath,
                "-o", outputFile.absolutePath
            )

            log.debug("Executing aggressive quality compression: q=${quality}")
            val success = executeWebPCommand(command)

            if (success && outputFile.exists()) {
                Files.readAllBytes(outputFile.toPath())
            } else {
                throw RuntimeException("cwebp failed to create output file")
            }
        } finally {
            // Cleanup temp files
            inputFile.delete()
            outputFile.delete()
        }
    }

    private fun compressWithCwebpLossless(inputBytes: ByteArray, effort: Int, method: Int): ByteArray {
        // Create temp files
        val inputFile = File.createTempFile("webp-input", ".webp")
        val outputFile = File.createTempFile("webp-output", ".webp")

        return try {
            // Write input to temp file
            Files.write(inputFile.toPath(), inputBytes)

            val command = listOf(
                "cwebp",
                "-lossless",
                "-z", effort.toString(),
                "-m", method.toString(),
                "-quiet",
                inputFile.absolutePath,
                "-o", outputFile.absolutePath
            )

            val success = executeWebPCommand(command)

            if (success && outputFile.exists()) {
                Files.readAllBytes(outputFile.toPath())
            } else {
                throw RuntimeException("cwebp failed to create output file")
            }
        } finally {
            // Cleanup temp files
            inputFile.delete()
            outputFile.delete()
        }
    }

    private fun executeWebPCommand(command: List<String>): Boolean {
        log.debug("Executing cwebp command: ${command.joinToString(" ")}")

        return try {
            val process = ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

            val finished = process.waitFor(30, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                log.error("cwebp process timeout")
                return false
            }

            val exitValue = process.exitValue()
            if (exitValue != 0) {
                val errorMsg = process.errorStream.bufferedReader().readText()
                log.error("cwebp command failed with exit code $exitValue: ${command.joinToString(" ")}")
                log.error("cwebp error: $errorMsg")
                return false
            }

            true
        } catch (e: Exception) {
            log.error("Failed to execute cwebp command: ${e.message}")
            false
        }
    }
}