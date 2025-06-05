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
        logger.info("Temporary output file created: ${tempOutputFile.absolutePath}")

        try {
            // Command options for cwebp based on compression type
            val command =
                when (compressionType) {
                    CompressionType.LOSSY -> {
                        listOf(
                            "cwebp",
                            "-q",
                            // Balance quality and size
                            "80",
                            "-m",
                            "6", // Compression method, higher values spend more time searching for
                            // better compression
                            "-o",
                            tempOutputFile.absolutePath, // Output file
                            inputFile.absolutePath, // Input file
                        )
                    }

                    CompressionType.LOSSLESS -> {
                        listOf(
                            "cwebp",
                            "-q",
                            // Slightly higher quality for lossless conversion
                            "85",
                            "-m",
                            "6", // Compression method, higher values spend more time searching for
                            // better compression
                            "-o",
                            tempOutputFile.absolutePath,
                            inputFile.absolutePath,
                        )
                    }
                }

            logger.info("ProcessBuilder command: $command")

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD)

            val process = processBuilder.start()
            val exitCode =
                if (process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.exitValue()
                } else {
                    process.destroyForcibly()
                    throw RuntimeException("cwebp process timeout")
                }

            logger.info("cwebp process exited with code $exitCode")
            if (exitCode != 0) {
                val errorMsg = process.inputStream.bufferedReader().readText()
                logger.error("cwebp failed: $errorMsg")
                throw RuntimeException("cwebp failed with exit code $exitCode")
            }

            // The compressed file should now be in the destination directory (same as input)
            val compressedBytes = Files.readAllBytes(tempOutputFile.toPath())
            logger.info("Compressed file size: ${compressedBytes.size} bytes")

            return compressedBytes
        } catch (e: IOException) {
            logger.error("Error during WebP compression", e)
            throw RuntimeException("Error during WebP compression: ${e.message}", e)
        } finally {
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
                logger.info("Temporary output file deleted")
            }
        }
    }
}
