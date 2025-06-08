package dev.tinify.service.compressionService.compressors.v2

import dev.tinify.CompressionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
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

        return try {
            val result = when (mode) {
                CompressionType.LOSSY -> compressWithCwebp(inputBytes, false)
                CompressionType.LOSSLESS -> compressWithCwebp(inputBytes, true)
            }

            val duration = System.currentTimeMillis() - startTime
            log.info("Fast WebP compression: ${inputBytes.size} B → ${result.size} B (${duration}ms)")

            result
        } catch (e: Exception) {
            log.error("Fast WebP compression failed, using original: ${e.message}")
            inputBytes
        }
    }

    private fun compressWithCwebp(inputBytes: ByteArray, lossless: Boolean): ByteArray {
        val command = buildList {
            add("cwebp")

            if (lossless) {
                add("-lossless")
                add("-z")
                add("9") // Best compression for lossless
            } else {
                add("-quality")
                add("80") // TinyPNG-equivalent quality
                add("-m")
                add("6") // Best compression method
            }

            add("-quiet") // Suppress output
            add("-o")
            add("/dev/stdout") // Output to stdout
            add("/dev/stdin") // Input from stdin
        }

        log.debug("Executing cwebp command: ${command.joinToString(" ")}")

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
            throw RuntimeException("cwebp process timeout")
        }

        if (process.exitValue() != 0) {
            val errorMsg = process.errorStream.bufferedReader().readText()
            throw RuntimeException("cwebp failed: $errorMsg")
        }

        return result
    }
}