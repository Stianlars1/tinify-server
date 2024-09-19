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
    val imageFile: BufferedImage?,
    val rawBytes: ByteArray?,
    val originalFileSize: Long,
)

@Service
class ImageService {
    val logger = LoggerFactory.getLogger(ImageService::class.java)

    @Throws(Exception::class)
    fun getImageFromRequest(file: MultipartFile): ImageRequestData {
        logger.debug("\n\n== service ==")
        logger.debug("= getImageFromRequest =")
        val contentType = file.contentType
        logger.debug("Content type: $contentType")

        if (contentType == null || !isSupportedContentType(contentType)) {
            logger.error("Unsupported image format: $contentType")
            throw IllegalArgumentException("Unsupported image format: $contentType")
        }

        val originalName = file.originalFilename ?: "image.${contentType.split("/").last()}"
        logger.debug("Original name: $originalName")
        val originalFormat = contentType.split("/").last()
        logger.debug("Original format: $originalFormat")

        val originalFileSize = file.size
        logger.debug("Original file size: $originalFileSize")

        val rawBytes = file.bytes

        return if (originalFormat.equals("gif", ignoreCase = true) && isAnimatedGif(rawBytes)) {
            logger.debug("Detected animated GIF")
            ImageRequestData(
                originalFormat = originalFormat,
                originalName = originalName,
                imageFile = null, // No BufferedImage for animated GIF
                rawBytes = rawBytes,
                originalFileSize = originalFileSize
            )
        } else {
            logger.debug("Processing as static image")
            val imageFile = try {
                val inputStream = ByteArrayInputStream(rawBytes)
                ImageIO.read(inputStream) ?: throw IllegalArgumentException("Failed to read image")
            } catch (e: Exception) {
                throw RuntimeException("Error converting MultipartFile to BufferedImage", e)
            }

            ImageRequestData(
                originalFormat = originalFormat,
                originalName = originalName,
                imageFile = imageFile,
                rawBytes = null, // No raw bytes for static images
                originalFileSize = originalFileSize
            )
        }
    }


    private fun isAnimatedGif(bytes: ByteArray): Boolean {
        try {
            val inputStream = ByteArrayInputStream(bytes)
            val reader = ImageIO.getImageReadersByFormatName("gif").next()
            reader.input = ImageIO.createImageInputStream(inputStream)
            val numFrames = reader.getNumImages(true)
            reader.dispose()
            return numFrames > 1
        } catch (e: Exception) {
            logger.error("Error checking if GIF is animated", e)
            return false
        }
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