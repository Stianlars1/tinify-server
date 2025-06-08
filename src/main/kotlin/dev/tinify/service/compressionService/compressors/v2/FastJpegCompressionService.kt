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

        return try {
            val result = when (mode) {
                CompressionType.LOSSY -> compressWithMozjpeg(inputBytes, 75) // TinyPNG-like quality
                CompressionType.LOSSLESS -> compressWithJpegtran(inputBytes)
            }

            val duration = System.currentTimeMillis() - startTime
            log.info("Fast JPEG compression: ${inputBytes.size} B → ${result.size} B (${duration}ms)")

            result
        } catch (e: Exception) {
            log.error("Fast JPEG compression failed, using original: ${e.message}")
            inputBytes
        }
    }

    private fun compressWithMozjpeg(inputBytes: ByteArray, quality: Int): ByteArray {
        val command = listOf(
            "/opt/mozjpeg/bin/cjpeg",
            "-quality", quality.toString(),
            "-optimize",
            "-progressive",
            "-outfile", "/dev/stdout", // Write to stdout
            "/dev/stdin" // Read from stdin
        )

        return executeImageCommand(command, inputBytes)
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

        return executeImageCommand(command, inputBytes)
    }

    private fun executeImageCommand(command: List<String>, inputBytes: ByteArray): ByteArray {
        log.debug("Executing command: ${command.joinToString(" ")}")

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
            throw RuntimeException("Process timeout")
        }

        if (process.exitValue() != 0) {
            val errorMsg = process.errorStream.bufferedReader().readText()
            throw RuntimeException("Command failed: $errorMsg")
        }

        return result
    }
}