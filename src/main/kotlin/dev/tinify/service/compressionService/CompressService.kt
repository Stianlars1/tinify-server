package dev.tinify.service

import dev.tinify.CompressionType
import dev.tinify.service.compressionService.compressors.GifCompressionService
import dev.tinify.service.compressionService.compressors.JpegCompressionService
import dev.tinify.service.compressionService.compressors.PngCompressionService
import dev.tinify.service.compressionService.compressors.WebPCompressionService
import dev.tinify.service.compressionService.createTempFileWithUniqueName
import dev.tinify.storage.FileStorageService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
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
    private val webpCompressionService: WebPCompressionService,
    private val gifCompressionService: GifCompressionService,
) {
    private val logger: Logger = LoggerFactory.getLogger(CompressService::class.java)

    fun compressImage(
        imageRequestData: ImageRequestData,
        compressionType: CompressionType,
    ): CompressionResult {
        logger.info("Starting image compression")

        val compressedBytes =
            if (imageRequestData.originalFormat.equals("gif", ignoreCase = true) && imageRequestData.rawBytes != null) {
                logger.debug("Processing as animated GIF")
                gifCompressionService.compressGifUsingGifsicle(imageRequestData.rawBytes, compressionType)
            } else {
                logger.debug("Processing as static image")
                // For static images, proceed as before
                val imageFile = imageRequestData.imageFile
                    ?: throw IllegalArgumentException("BufferedImage is null for non-GIF image")
                val originalFormat = imageRequestData.originalFormat
                val compressionTypeLocal = compressionType
                compressBasedOnFormat(imageRequestData, compressionTypeLocal)
            }

        val compressedSize = compressedBytes.size.toLong()
        val compressionPercentage = if (imageRequestData.originalFileSize > 0) {
            100.0 * (imageRequestData.originalFileSize - compressedSize) / imageRequestData.originalFileSize
        } else {
            0.0
        }

        return CompressionResult(
            compressedData = compressedBytes,
            originalSize = imageRequestData.originalFileSize,
            compressedSize = compressedSize,
            compressionPercentage = compressionPercentage
        )
    }

    private fun compressBasedOnFormat(
        imageRequestData: ImageRequestData,
        compressionType: CompressionType,
    ): ByteArray {
        val imageFile = imageRequestData.imageFile
            ?: throw IllegalArgumentException("BufferedImage is null for static image")
        val originalFormat = imageRequestData.originalFormat
        val originalFileName = imageRequestData.originalName

        val tempInputFile = createTempFileWithUniqueName(originalFileName, originalFormat)
        ImageIO.write(imageFile, originalFormat, tempInputFile)

        val compressedBytes = try {
            when (originalFormat.lowercase()) {
                "png" -> {
                    if (compressionType == CompressionType.LOSSLESS) {
                        pngCompressionService.compressPngUsingOptiPNG(tempInputFile)
                    } else {
                        pngCompressionService.compressPngUsingPngQuant(tempInputFile)
                    }
                }

                "jpeg", "jpg" -> {
                    jpegCompressionService.compressJpegUsingJpegOptim(tempInputFile, compressionType)
                }

                "webp" -> {
                    webpCompressionService.compressWebPUsingCwebp(tempInputFile, compressionType)
                }

                else -> throw IllegalArgumentException("Unsupported image format: $originalFormat")
            }
        } catch (e: Exception) {
            tempInputFile.delete()
            throw e
        }

        tempInputFile.delete()
        return compressedBytes
    }


}
