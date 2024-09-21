package dev.tinify.service

import dev.tinify.CompressionType
import dev.tinify.getCompressionPercent
import dev.tinify.service.compressionService.compressors.*
import dev.tinify.service.compressionService.createTempFileWithUniqueName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.imageio.ImageIO

data class CompressionResult(
    val compressedData: ByteArray,
    val originalSize: Long,
    val compressedSize: Long,
    val compressionPercentage: Double,
)

@Service
class CompressService(
    private val pngCompressionService: PngCompressionService, // Inject the new service
    private val jpegCompressionService: JpegCompressionService,
    private val webpCompressionService: WebPCompressionService,
    private val gifCompressionService: GifCompressionService,
    private val tiffCompressionService: TiffCompressionService,
    private val imageMagickFallbackService: ImageMagickFallbackService,
    private val svgOptimizeService: SvgOptimizeService,
) {
    private val logger: Logger = LoggerFactory.getLogger(CompressService::class.java)

    fun compressImage(
        imageRequestData: ImageRequestData,
        compressionType: CompressionType,
    ): CompressionResult {
        logger.info("Starting image compression")

        val compressedBytes = compressBasedOnFormat(imageRequestData, compressionType)

        val compressedSize = compressedBytes.size.toLong()
        val compressionPercentage =
            if (imageRequestData.originalFileSize > 0) {
                getCompressionPercent(imageRequestData.originalFileSize, compressedSize)
            } else {
                0.0
            }

        return CompressionResult(
            compressedData = compressedBytes,
            originalSize = imageRequestData.originalFileSize,
            compressedSize = compressedSize,
            compressionPercentage = compressionPercentage,
        )
    }

    private fun compressBasedOnFormat(
        imageRequestData: ImageRequestData,
        compressionType: CompressionType,
    ): ByteArray {
        val originalFormat = imageRequestData.originalFormat
        val originalFileName = imageRequestData.originalName

        val rawBytes = imageRequestData.rawBytes
        val imageFile = imageRequestData.imageFile
        // Handle scenarios where we have raw bytes instead of an ImageIO supported format
        val tempInputFile =
            if (rawBytes != null) {
                // If rawBytes are available (for unsupported formats like SVG), write them to a
                // temporary file
                createTempFileWithRawBytes(rawBytes, originalFileName, originalFormat)
            } else if (imageFile != null) {
                // Otherwise, use the BufferedImage if it's available
                createTempFileWithUniqueName(originalFileName, originalFormat).apply {
                    ImageIO.write(imageFile, originalFormat, this)
                }
            } else {
                throw IllegalArgumentException(
                    "Both BufferedImage and rawBytes are null for the image"
                )
            }

        val compressedBytes =
            try {
                when (originalFormat.lowercase()) {
                    "png" -> {
                        pngCompressionService.compressPng(tempInputFile, compressionType)
                    }

                    "jpeg",
                    "jpg" ->
                        jpegCompressionService.compressJpegUsingJpegOptim(
                            tempInputFile,
                            compressionType,
                        )
                    "webp" ->
                        webpCompressionService.compressWebPUsingCwebp(
                            tempInputFile,
                            compressionType,
                        )
                    "tiff" -> tiffCompressionService.compressTiffUsingImageMagick(tempInputFile)
                    "gif" -> {
                        if (rawBytes == null) {
                            throw IllegalArgumentException("Raw bytes are null for GIF image")
                        }
                        gifCompressionService.compressGifUsingGifsicle(rawBytes, compressionType)
                    }

                    "svg",
                    "svg+xml" -> {
                        if (rawBytes == null) {
                            throw IllegalArgumentException("Raw bytes are null for svg+xml image")
                        }
                        return svgOptimizeService.compressSvg(imageRequestData.rawBytes)
                    }

                    else -> {
                        logger.warn(
                            "Unsupported format detected: $originalFormat, using fallback ImageMagick conversion"
                        )
                        imageMagickFallbackService.convertAndCompressUsingImageMagick(
                            tempInputFile,
                            compressionType,
                        )
                    }
                }
            } catch (e: Exception) {
                try {
                    logger.error(
                        "Error compressing image, falling back to ImageMagick: ${e.message}",
                        e,
                    )
                    return imageMagickFallbackService.convertAndCompressUsingImageMagick(
                        tempInputFile,
                        compressionType,
                    )
                } catch (e: Exception) {
                    logger.error(
                        "#2 - Error compressing fallback solution with ImageMagick: ${e.message}",
                        e,
                    )
                    tempInputFile.delete()
                    throw e
                }
            }

        tempInputFile.delete()
        return compressedBytes
    }
}

// Helper method to create a temp file with rawBytes
private fun createTempFileWithRawBytes(
    rawBytes: ByteArray,
    originalFileName: String,
    originalFormat: String,
): File {
    val tempFile =
        File.createTempFile("temp-${UUID.randomUUID()}-$originalFileName", ".$originalFormat")
    Files.write(tempFile.toPath(), rawBytes)
    return tempFile
}
