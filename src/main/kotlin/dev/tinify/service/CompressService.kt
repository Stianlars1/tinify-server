package dev.tinify.service

import dev.tinify.CompressionType
import dev.tinify.storage.FileStorageService
import org.slf4j.Logger
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO



data class CompressionResult(
    val compressedData: ByteArray,
    val originalSize: Long,
    val compressedSize: Long,
    val compressionPercentage: Double,
)

@Service
class CompressService(
    private val fileStorageService: FileStorageService,
) {
    val logger: Logger = org.slf4j.LoggerFactory.getLogger(CompressService::class.java)

    // Entry point for image compression, now returning CompressionResult
    fun compressImage(
        imageFile: BufferedImage,
        originalFileName: String,
        originalFormat: String,
        compressionType: CompressionType,
        originalFileSize: Long,
    ): CompressionResult {
        println("Compressing image")

        // Compress the image using the optimized compression logic
        val compressedImage = compressBufferedImage(imageFile, originalFormat, compressionType)

        // Step 4: Return the compressed image as a byte array so it can be sent back to the user
        // Get the compressed size in bytes
        val compressedSize = compressedImage.size.toLong()

        // Calculate compression percentage
        val compressionPercentage = if (originalFileSize > 0) {
            100.0 * (originalFileSize - compressedSize) / originalFileSize
        } else {
            0.0
        }

        // Return CompressionResult with compression details
        return CompressionResult(
            compressedData = compressedImage,
            originalSize = originalFileSize, // Use original file size here
            compressedSize = compressedSize,
            compressionPercentage = compressionPercentage,
        )
    }

    // Optimized image compression function
    private fun compressBufferedImage(
        image: BufferedImage,
        format: String,
        compressionType: CompressionType,
    ): ByteArray {
        println("\n\n== compressBufferedImage ==")
        logger.info("-- incoming format: $format")
        logger.info("-- incoming compressionType: $compressionType")

        // Get the original image as a byte array
        val originalBytes = toByteArray(image, format)

        // Try to compress the image, if supported
        val compressedBytes = try {
            compressBasedOnFormat(image, format, compressionType)
        } catch (e: Exception) {
            logger.error("Error during compression, using original image", e)
            originalBytes // If any error occurs, fallback to the original image
        }

        // Return the smaller of the two (original vs. compressed)
        return if (compressedBytes.size < originalBytes.size) {
            logger.info("Compressed image is smaller, returning compressed version")
            compressedBytes
        } else {
            logger.info("Original image is smaller, returning original version")
            originalBytes
        }
    }

    // Helper method to convert BufferedImage to byte array
    private fun toByteArray(image: BufferedImage, format: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, format, outputStream)
        return outputStream.toByteArray()
    }

    // Core compression logic based on format and type (LOSSLESS or LOSSY)
    private fun compressBasedOnFormat(
        image: BufferedImage,
        format: String,
        compressionType: CompressionType,
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()

        when (format.lowercase()) {
            "webp" -> {
                val writer = ImageIO.getImageWritersByFormatName("webp").next()
                val ios = ImageIO.createImageOutputStream(outputStream)
                writer.output = ios

                val writeParam = writer.defaultWriteParam
                writeParam.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                writeParam.compressionType = if (compressionType == CompressionType.LOSSLESS) "Lossless" else "Lossy"

                writer.write(null, IIOImage(image, null, null), writeParam)
                writer.dispose()
                ios.close()
            }

            "tiff" -> {
                val writer = ImageIO.getImageWritersByFormatName("tiff").next()
                val ios = ImageIO.createImageOutputStream(outputStream)
                writer.output = ios

                val writeParam = writer.defaultWriteParam
                writeParam.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                writeParam.compressionType = if (compressionType == CompressionType.LOSSLESS) "LZW" else "Deflate"

                writer.write(null, IIOImage(image, null, null), writeParam)
                writer.dispose()
                ios.close()
            }

            "jpeg", "jpg" -> {
                val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
                val ios = ImageIO.createImageOutputStream(outputStream)
                writer.output = ios

                val writeParam = writer.defaultWriteParam
                writeParam.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                writeParam.compressionQuality = if (compressionType == CompressionType.LOSSY) 0.75f else 1.0f

                writer.write(null, IIOImage(image, null, null), writeParam)
                writer.dispose()
                ios.close()
            }

            "png" -> {
                val writer = ImageIO.getImageWritersByFormatName("png").next()
                val ios = ImageIO.createImageOutputStream(outputStream)
                writer.output = ios

                val writeParam = writer.defaultWriteParam
                writeParam.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                writeParam.compressionQuality = if (compressionType == CompressionType.LOSSY) 0.75f else 1.0f

                writer.write(null, IIOImage(image, null, null), writeParam)
                writer.dispose()
                ios.close()
            }

            else -> {
                if (ImageIO.getImageWritersByFormatName(format).hasNext()) {
                    ImageIO.write(image, format, outputStream) // Default write without compression
                } else {
                    throw IllegalArgumentException("Unsupported image format: $format")
                }
            }
        }

        return outputStream.toByteArray()
    }
}
