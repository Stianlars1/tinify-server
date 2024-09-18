package dev.tinify.service

import dev.tinify.ImageProcessingResult
import dev.tinify.storage.FileStorageService
import dev.tinify.writeImageWithFallback
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage

@Service
class CropService(private val fileStorageService: FileStorageService) {
    private val logger = LoggerFactory.getLogger(CropService::class.java)
    fun cropImage(
        imageFile: BufferedImage,
        originalFileName: String,
        format: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): ImageProcessingResult {
        logger.debug("\n\n== service ==")
        logger.debug("= cropImage =")
        // Validate the cropping rectangle
        if (x < 0 || y < 0 || width <= 0 || height <= 0 ||
            x + width > imageFile.width || y + height > imageFile.height
        ) {
            logger.error("Invalid cropping rectangle.")
            throw IllegalArgumentException("Invalid cropping rectangle.")
        }

        // Perform the cropping operation
        val croppedImage = imageFile.getSubimage(x, y, width, height)
        // Handle format-specific writing
        val result = writeImageWithFallback(croppedImage, format)

        // Store the image and get unique filename
        val uniqueFileName = fileStorageService.storeImageAndScheduleDeletion(
            result.imageBytes,
            originalFileName,
            result.format
        )
        logger.debug("Unique filename: $uniqueFileName")
        // Use writeImageWithFallback to handle potential format issues

        // Return result with unique filename
        return result.copy(uniqueFileName = uniqueFileName)
    }

}
