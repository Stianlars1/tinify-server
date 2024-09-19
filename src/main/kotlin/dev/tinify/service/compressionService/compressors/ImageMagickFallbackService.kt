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
class ImageMagickFallbackService {

    private val logger: Logger = LoggerFactory.getLogger(ImageMagickFallbackService::class.java)

    fun convertAndCompressUsingImageMagick(
        inputFile: File,
        compressionType: CompressionType,
    ): ByteArray {
        logger.info("Attempting to convert and compress unsupported format using ImageMagick")

        val tempOutputFile =
            File.createTempFile(
                "converted-${UUID.randomUUID()}",
                ".jpg",
            ) // Using JPEG as fallback format
        logger.info("Temporary output file created: ${tempOutputFile.absolutePath}")

        try {
            // Use ImageMagick to convert the image to a better supported format like JPEG or PNG
            val command =
                listOf(
                    "convert",
                    inputFile.absolutePath,
                    "-strip",
                    "-type",
                    "Palette", // Strip metadata and convert to palette type
                    "-compress",
                    "JPEG",
                    "-quality",
                    if (compressionType == CompressionType.LOSSLESS) "80"
                    else "70", // Lossless or lossy based on input
                    tempOutputFile.absolutePath,
                )

            logger.info("ProcessBuilder command: $command")

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                val errorMsg = process.inputStream.bufferedReader().readText()
                logger.error("ImageMagick conversion failed: $errorMsg")
                throw RuntimeException("ImageMagick conversion failed with exit code $exitCode")
            }

            val compressedBytes = Files.readAllBytes(tempOutputFile.toPath())
            logger.info("Converted and compressed file size: ${compressedBytes.size} bytes")

            return compressedBytes
        } catch (e: IOException) {
            logger.error("Error during conversion and compression", e)
            throw RuntimeException("Error during conversion and compression: ${e.message}", e)
        } finally {
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
                logger.info("Temporary output file deleted")
            }
        }
    }
}
