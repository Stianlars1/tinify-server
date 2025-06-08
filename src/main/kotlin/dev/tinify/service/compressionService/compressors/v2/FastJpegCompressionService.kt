package dev.tinify.service.compressionService.compressors.v2

import dev.tinify.CompressionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.TimeUnit

@Service
class FastJpegCompressionService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun compressJpegInMemory(input: File, mode: CompressionType): ByteArray {
        val inputBytes = input.readBytes()
        return compressJpegFromBytes(inputBytes, mode)
    }

    fun compressJpegFromBytes(inputBytes: ByteArray, mode: CompressionType): ByteArray {
        val startTime = System.currentTimeMillis()
        val originalSize = inputBytes.size

        return try {
            val result = when (mode) {
                CompressionType.LOSSY -> findBestLossyCompression(inputBytes)
                CompressionType.LOSSLESS -> findBestLosslessCompression(inputBytes)
            }

            val duration = System.currentTimeMillis() - startTime

            // CRITICAL: Always return original if compressed is not smaller
            if (result.size >= originalSize) {
                log.info("Compressed JPEG was not smaller (${result.size} B >= ${originalSize} B), returning original")
                return inputBytes
            }

            log.info("Fast JPEG compression: ${originalSize} B → ${result.size} B (${duration}ms)")
            result
        } catch (e: Exception) {
            log.error("Fast JPEG compression failed, using original: ${e.message}")
            inputBytes
        }
    }

    private fun findBestLossyCompression(inputBytes: ByteArray): ByteArray {
        // TinyPNG uses different quality levels for different situations
        val compressionAttempts = listOf(
            Triple(85, true, true),   // High quality + mozjpeg + jpegoptim
            Triple(80, true, true),   // TinyPNG standard + mozjpeg + jpegoptim
            Triple(75, true, false),  // Lower quality + mozjpeg only
            Triple(80, false, true),  // Fallback: cjpeg + jpegoptim
        )

        var bestResult = inputBytes
        var bestSize = inputBytes.size

        for ((quality, useMozjpeg, useJpegoptim) in compressionAttempts) {
            try {
                val compressed = if (useMozjpeg) {
                    compressWithMozjpeg(inputBytes, quality)
                } else {
                    compressWithCjpeg(inputBytes, quality)
                }

                val finalResult = if (useJpegoptim && compressed.size < bestSize) {
                    applyJpegoptim(compressed) ?: compressed
                } else {
                    compressed
                }

                if (finalResult.size < bestSize) {
                    bestResult = finalResult
                    bestSize = finalResult.size
                    log.debug("New best JPEG compression: ${bestSize} bytes with quality=$quality, mozjpeg=$useMozjpeg, jpegoptim=$useJpegoptim")
                }
            } catch (e: Exception) {
                log.debug("JPEG compression attempt failed with quality=$quality: ${e.message}")
                continue
            }
        }

        return bestResult
    }

    private fun findBestLosslessCompression(inputBytes: ByteArray): ByteArray {
        val attempts = listOf(
            Pair(true, true),   // jpegtran + jpegoptim
            Pair(true, false),  // jpegtran only
            Pair(false, true),  // jpegoptim only
        )

        var bestResult = inputBytes
        var bestSize = inputBytes.size

        for ((useJpegtran, useJpegoptim) in attempts) {
            try {
                val compressed = if (useJpegtran) {
                    compressWithJpegtran(inputBytes)
                } else {
                    inputBytes
                }

                val finalResult = if (useJpegoptim && compressed.size < bestSize) {
                    applyJpegoptim(compressed) ?: compressed
                } else {
                    compressed
                }

                if (finalResult.size < bestSize) {
                    bestResult = finalResult
                    bestSize = finalResult.size
                }
            } catch (e: Exception) {
                log.debug("JPEG lossless compression attempt failed: ${e.message}")
                continue
            }
        }

        return bestResult
    }

    private fun compressWithMozjpeg(inputBytes: ByteArray, quality: Int): ByteArray {
        val command = listOf(
            "/opt/mozjpeg/bin/cjpeg",
            "-quality", quality.toString(),
            "-optimize",
            "-progressive",
            "-outfile", "/dev/stdout",
            "/dev/stdin"
        )

        return executeImageCommand(command, inputBytes, "mozjpeg")
    }

    private fun compressWithCjpeg(inputBytes: ByteArray, quality: Int): ByteArray {
        val command = listOf(
            "cjpeg",
            "-quality", quality.toString(),
            "-optimize",
            "-progressive",
            "-outfile", "/dev/stdout",
            "/dev/stdin"
        )

        return executeImageCommand(command, inputBytes, "cjpeg")
    }

    private fun compressWithJpegtran(inputBytes: ByteArray): ByteArray {
        val command = listOf(
            "/opt/mozjpeg/bin/jpegtran",
            "-copy", "none",
            "-optimize",
            "-progressive",
            "-outfile", "/dev/stdout",
            "/dev/stdin"
        )

        return executeImageCommand(command, inputBytes, "jpegtran")
    }

    private fun applyJpegoptim(inputBytes: ByteArray): ByteArray? {
        return try {
            val command = listOf(
                "jpegoptim",
                "--strip-all",
                "--all-progressive",
                "--stdout",
                "/dev/stdin"
            )

            executeImageCommand(command, inputBytes, "jpegoptim")
        } catch (e: Exception) {
            log.debug("jpegoptim compression failed: ${e.message}")
            null
        }
    }

    private fun executeImageCommand(command: List<String>, inputBytes: ByteArray, toolName: String): ByteArray {
        log.debug("Executing $toolName command: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        // Write input to stdin
        process.outputStream.use { it.write(inputBytes) }

        // Read result from stdout
        val result = process.inputStream.readAllBytes()

        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw RuntimeException("$toolName process timeout")
        }

        if (process.exitValue() != 0) {
            val errorMsg = process.errorStream.bufferedReader().readText()
            throw RuntimeException("$toolName failed: $errorMsg")
        }

        return result
    }
}