package dev.tinify.service.compressionService.compressors

import dev.tinify.CompressionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*

@Service
class GifCompressionService {

    private val logger = LoggerFactory.getLogger(GifCompressionService::class.java)

    fun compressGifUsingGifsicle(
        inputBytes: ByteArray,
        compressionType: CompressionType,
    ): ByteArray {
        logger.info("Compressing GIF using gifsicle")

        val tempInputFile = File.createTempFile("input-${UUID.randomUUID()}", ".gif")
        val tempOutputFile = File.createTempFile("compressed-${UUID.randomUUID()}", ".gif")
        logger.info("Temporary input file created: ${tempInputFile.absolutePath}")
        logger.info("Temporary output file created: ${tempOutputFile.absolutePath}")

        try {
            // Write input bytes to temporary input file
            Files.write(tempInputFile.toPath(), inputBytes)

            // Build the gifsicle command
            val command =
                mutableListOf(
                    "gifsicle",
                    "--optimize=3", // Maximize optimization level
                    "--use-col=web", // Use web-safe color palette
                    "--lossy=10", // Enable lossy compression
                    "--loopcount=0", // Ensure animation loops forever
                    "--output",
                    tempOutputFile.absolutePath, // Output file path
                    tempInputFile.absolutePath, // Input file path
                )

            logger.info("ProcessBuilder command: $command")

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                val errorMsg = process.inputStream.bufferedReader().readText()
                logger.error("gifsicle failed: $errorMsg")
                throw RuntimeException("gifsicle failed with exit code $exitCode")
            }

            val compressedBytes = Files.readAllBytes(tempOutputFile.toPath())
            logger.info("Compressed GIF file size: ${compressedBytes.size} bytes")

            return compressedBytes
        } catch (e: IOException) {
            logger.error("Error during GIF compression", e)
            throw RuntimeException("Error during GIF compression: ${e.message}", e)
        } finally {
            // Clean up temporary files
            if (tempInputFile.exists()) {
                tempInputFile.delete()
                logger.info("Temporary input file deleted")
            }
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
                logger.info("Temporary output file deleted")
            }
        }
    }
}
