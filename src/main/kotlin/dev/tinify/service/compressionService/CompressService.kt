package dev.tinify.service

import dev.tinify.CompressionType
import dev.tinify.service.compressionService.compressors.JpegCompressionService
import dev.tinify.service.compressionService.compressors.PngCompressionService
import dev.tinify.service.compressionService.createTempFileWithUniqueName
import dev.tinify.storage.FileStorageService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.File
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
    private val pngCompressionService: PngCompressionService, // Inject the new service
    private val jpegCompressionService: JpegCompressionService,
) {
    private val logger: Logger = LoggerFactory.getLogger(CompressService::class.java)

    fun compressImage(
        imageFile: BufferedImage,
        originalFileName: String,
        originalFormat: String,
        compressionType: CompressionType,
        originalFileSize: Long,
    ): CompressionResult {
        logger.info("Starting image compression")

        // Create a temporary input file
        val tempInputFile = createTempFileWithUniqueName(originalFileName, originalFormat)
        ImageIO.write(imageFile, originalFormat, tempInputFile)

        // Compress the image using the appropriate method
        val compressedBytes = try {
            compressBasedOnFormat(tempInputFile, originalFormat, compressionType)
        } catch (e: Exception) {
            tempInputFile.delete()
            throw e
        }

        val compressedSize = compressedBytes.size.toLong()
        val compressionPercentage = if (originalFileSize > 0) {
            100.0 * (originalFileSize - compressedSize) / originalFileSize
        } else {
            0.0
        }

        tempInputFile.delete()

        return CompressionResult(compressedBytes, originalFileSize, compressedSize, compressionPercentage)
    }

    private fun compressBasedOnFormat(
        inputFile: File,
        format: String,
        compressionType: CompressionType,
    ): ByteArray {
        return when (format.lowercase()) {
            "png" -> {
                if (compressionType == CompressionType.LOSSLESS) {
                    pngCompressionService.compressPngUsingOptiPNG(inputFile)
                } else {
                    pngCompressionService.compressPngUsingPngQuant(inputFile)
                }
            }

            "jpeg", "jpg" -> {
                jpegCompressionService.compressJpegUsingJpegOptim(inputFile, compressionType)
            }

            else -> throw IllegalArgumentException("Unsupported image format: $format")
        }
    }


}
