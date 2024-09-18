package dev.tinify.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

data class ImageRequestData(
    val originalFormat: String,
    val originalName: String,
    val imageFile: BufferedImage,
    val originalFileSize: Long,
)

@Service
class ImageService {
    val logger = LoggerFactory.getLogger(ImageService::class.java)

    @Throws(Exception::class)
    fun getImageFromRequest(file: MultipartFile): ImageRequestData {
        logger.debug("\n\n== service ==")
        logger.debug("= getImageFromRequest =")
        // Validate that the content type is an accepted image format
        val contentType = file.contentType
        logger.debug("Content type: $contentType")
        if (contentType == null || !isSupportedContentType(contentType)) {
            logger.error("Unsupported image format: $contentType")
            throw IllegalArgumentException("Unsupported image format: $contentType")
        }

        // Extract the original file name
        val originalName = file.originalFilename ?: "image.jpeg"
        logger.debug("Original name: $originalName")

        // Extract the original format from the content type
        val originalFormat = contentType.split("/").last()
        logger.debug("Original format: $originalFormat")

        // Convert MultipartFile to BufferedImage for further processing
        val imageFile = try {
            val imageBytes = file.bytes
            val inputStream = ByteArrayInputStream(imageBytes)
            ImageIO.read(inputStream) ?: throw IllegalArgumentException("Failed to read image")
        } catch (e: Exception) {
            throw RuntimeException("Error converting MultipartFile to BufferedImage", e)
        }

        // Get the original file size from the MultipartFile
        val originalFileSize = file.size
        logger.debug("Original file size: $originalFileSize")

        // Return original format, original name, and BufferedImage
        // Return ImageRequestData with all information
        return ImageRequestData(
            originalFormat = originalFormat,
            originalName = originalName,
            imageFile = imageFile,
            originalFileSize = originalFileSize // Return the file size from the original file
        )
    }

    private fun isSupportedContentType(contentType: String): Boolean {
        return contentType == "image/jpeg" ||
                contentType == "image/jpg" ||
                contentType == "image/png" ||
                contentType == "image/gif" ||
                contentType == "image/tiff" ||
                contentType == "image/webp"
    }

}