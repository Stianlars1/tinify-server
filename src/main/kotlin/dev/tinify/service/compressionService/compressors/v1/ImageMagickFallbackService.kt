package dev.tinify.service.compressionService.compressors.v1

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
        outputFormat: String = "jpg",
    ): ByteArray {
        logger.info("Attempting to convert and compress unsupported format using ImageMagick")

        val tempOutputFile =
            File.createTempFile(
                "converted-${UUID.randomUUID()}",
                ".$outputFormat",
            )
        logger.info("Temporary output file created: ${tempOutputFile.absolutePath}")

        try {
            // Use ImageMagick to convert the image while preserving the original format when possible
            val command = mutableListOf(
                "convert",
                inputFile.absolutePath,
                "-strip",
            )

            if (outputFormat.equals("png", true)) {
                // PNG supports transparency
                command.addAll(listOf("-quality", if (compressionType == CompressionType.LOSSLESS) "100" else "90"))
            } else {
                command.addAll(
                    listOf(
                        "-type",
                        "Palette",
                        "-compress",
                        "JPEG",
                        "-quality",
                        if (compressionType == CompressionType.LOSSLESS) "80" else "70",
                    )
                )
            }

            command.add(tempOutputFile.absolutePath)

            logger.info("ProcessBuilder command: $command")

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD)

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
