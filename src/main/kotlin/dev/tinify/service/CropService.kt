package dev.tinify.service

import dev.tinify.storage.FileStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files

@Service
class CropService(private val fileStorageService: FileStorageService) {
    private val logger = LoggerFactory.getLogger(CropService::class.java)

    fun cropImage(
        imageFile: File, // Pass the file directly
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): ByteArray {

        try {
            logger.debug("Cropping image with x: $x, y: $y, width: $width, height: $height")

            // Create a temporary file for the output
            val outputFile = File.createTempFile("cropped-", ".${getFileExtension(imageFile)}")

            // Prepare the ImageMagick command to crop the image
            val command =
                listOf(
                    "convert",
                    imageFile.absolutePath,
                    "-crop",
                    "${width}x${height}+$x+$y", // Define the cropping rectangle
                    outputFile.absolutePath,
                )
            logger.debug("ImageMagick CROP command: $command")

            // Execute the ImageMagick command
            val process = ProcessBuilder(command).start()
            process.waitFor()

            if (process.exitValue() != 0) {
                logger.error(
                    "ImageMagick cropping failed: ${process.errorStream.bufferedReader().readText()}"
                )
                throw RuntimeException(
                    "ImageMagick cropping failed: ${
                        process.errorStream.bufferedReader().readText()
                    }"
                )
            }

            // Read the cropped image as bytes
            val croppedImageBytes = Files.readAllBytes(outputFile.toPath())
            logger.debug("Cropped image size: ${croppedImageBytes.size} bytes")
            return croppedImageBytes
        } catch (e: Exception) {
            logger.error("Error cropping image: ${e.message}")
            throw e
        }
    }

    // Utility function to get the file extension
    fun getFileExtension(file: File): String {
        logger.debug("Getting file extension for: ${file.name}")
        val fileName =
            file.extension.ifEmpty { file.name } // Use the file name if extension is missing
        logger.debug("File name: $fileName")
        return fileName
    }
}
