// WebPCompressionService.kt (updated)
package dev.tinify.service.compressionService.compressors

import dev.tinify.CompressionType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*

@Service
class WebPCompressionService {

    private val logger: Logger = LoggerFactory.getLogger(WebPCompressionService::class.java)

    fun compressWebPUsingCwebp(inputFile: File, compressionType: CompressionType): ByteArray {
        logger.info("Compressing WebP using cwebp")
        val tempOutputFile = File.createTempFile("compressed-${UUID.randomUUID()}", ".webp")

        try {
            val command = when (compressionType) {
                CompressionType.LOSSY -> listOf(
                    "cwebp", "-mt", "-q", "70", "-m", "6",
                    "-o", tempOutputFile.absolutePath, inputFile.absolutePath
                )

                CompressionType.LOSSLESS -> listOf(
                    "cwebp", "-mt", "-lossless", "-q", "100", "-m", "6",
                    "-o", tempOutputFile.absolutePath, inputFile.absolutePath
                )
            }
            logger.info("Executing command: ${command.joinToString(" ")}")
            val process = ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw RuntimeException("cwebp process timeout")
            }
            if (process.exitValue() != 0) {
                val err = process.inputStream.bufferedReader().readText()
                logger.error("cwebp failed: $err")
                throw RuntimeException("cwebp failed (code ${process.exitValue()})")
            }
            val compressedBytes = Files.readAllBytes(tempOutputFile.toPath())
            logger.info("Compressed WebP file size: ${compressedBytes.size} bytes")
            return compressedBytes
        } catch (e: IOException) {
            logger.error("Error during WebP compression", e)
            throw RuntimeException("Error during WebP compression: ${e.message}", e)
        } finally {
            tempOutputFile.delete()
        }
    }
}
