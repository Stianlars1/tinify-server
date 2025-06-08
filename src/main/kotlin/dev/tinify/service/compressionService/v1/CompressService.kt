package dev.tinify.service

import dev.tinify.CompressionType
import dev.tinify.getCompressionPercent
import dev.tinify.service.compressionService.compressors.v1.*
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
    private val pngCompressionService: PngCompressionService,
    private val jpegCompressionService: JpegCompressionService,
    private val webpCompressionService: WebPCompressionService,
    private val gifCompressionService: GifCompressionService,
    private val tiffCompressionService: TiffCompressionService,
    private val imageMagickFallbackService: ImageMagickFallbackService,
    private val svgOptimizeService: SvgOptimizeService,
    private val pdfCompressionService: PdfCompressionService,
) {
    private val logger: Logger = LoggerFactory.getLogger(CompressService::class.java)

    fun compressImage(
        imageRequestData: ImageRequestData,
        compressionType: CompressionType,
    ): CompressionResult {
        logger.info("Starting TinyPNG-style compression for ${imageRequestData.originalFormat}")

        val compressedBytes = compressBasedOnFormat(imageRequestData, compressionType)

        val compressedSize = compressedBytes.size.toLong()
        val compressionPercentage = if (imageRequestData.originalFileSize > 0) {
            getCompressionPercent(imageRequestData.originalFileSize, compressedSize)
        } else {
            0.0
        }

        logger.info("Compression complete: ${imageRequestData.originalFileSize} B → $compressedSize B (${compressionPercentage.toInt()}% reduction)")

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

        val tempInputFile = if (rawBytes != null) {
            createTempFileWithRawBytes(rawBytes, originalFileName, originalFormat)
        } else if (imageFile != null) {
            createTempFileWithUniqueName(originalFileName, originalFormat).apply {
                ImageIO.write(imageFile, originalFormat, this)
            }
        } else {
            throw IllegalArgumentException("Both BufferedImage and rawBytes are null for the image")
        }

        val compressedBytes = try {
            when (originalFormat.lowercase()) {
                "png" -> {
                    logger.info("Using TinyPNG-style PNG compression")
                    pngCompressionService.compressPng(tempInputFile, compressionType)
                }

                "pdf" -> {
                    pdfCompressionService.compressPdf(tempInputFile)
                }

                "jpeg", "jpg" -> {
                    logger.info("Using TinyPNG-style JPEG compression")
                    jpegCompressionService.compressJpegUsingMozjpeg(tempInputFile, compressionType)
                }

                "webp" -> webpCompressionService.compressWebPUsingCwebp(tempInputFile, compressionType)

                "tiff" -> tiffCompressionService.compressTiffUsingImageMagick(tempInputFile)
                "gif" -> {
                    if (rawBytes == null) {
                        throw IllegalArgumentException("Raw bytes are null for GIF image")
                    }
                    gifCompressionService.compressGifUsingGifsicle(rawBytes, compressionType)
                }

                "svg", "svg+xml" -> {
                    if (rawBytes == null) {
                        throw IllegalArgumentException("Raw bytes are null for svg+xml image")
                    }
                    return svgOptimizeService.compressSvg(imageRequestData.rawBytes)
                }

                else -> {
                    logger.warn("Unsupported format: $originalFormat, using ImageMagick fallback")
                    imageMagickFallbackService.convertAndCompressUsingImageMagick(
                        tempInputFile, compressionType, originalFormat
                    )
                }
            }
        } catch (e: Exception) {
            try {
                logger.error("Primary compression failed, falling back to ImageMagick: ${e.message}")
                imageMagickFallbackService.convertAndCompressUsingImageMagick(
                    tempInputFile, compressionType, originalFormat
                )
            } catch (fallbackException: Exception) {
                logger.error("ImageMagick fallback also failed: ${fallbackException.message}")
                tempInputFile.delete()
                throw fallbackException
            }
        }

        tempInputFile.delete()
        return compressedBytes
    }
}

private fun createTempFileWithRawBytes(
    rawBytes: ByteArray,
    originalFileName: String,
    originalFormat: String,
): File {
    val tempFile = File.createTempFile("temp-${UUID.randomUUID()}-$originalFileName", ".$originalFormat")
    Files.write(tempFile.toPath(), rawBytes)
    return tempFile
}