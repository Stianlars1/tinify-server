package dev.tinify.service.compressionService.v2

import dev.tinify.CompressionType
import dev.tinify.getCompressionPercent
import dev.tinify.service.CompressionResult
import dev.tinify.service.ImageRequestData
import dev.tinify.service.compressionService.compressors.v1.*
import dev.tinify.service.compressionService.compressors.v2.FastJpegCompressionService
import dev.tinify.service.compressionService.compressors.v2.FastPngCompressionService
import dev.tinify.service.compressionService.compressors.v2.FastWebPCompressionService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files

@Service
class CompressServiceV2(
    private val fastPngCompressionService: FastPngCompressionService,
    private val fastJpegCompressionService: FastJpegCompressionService,
    private val fastWebPCompressionService: FastWebPCompressionService,
    // Keep fallback services for unsupported formats
    private val gifCompressionService: GifCompressionService,
    private val tiffCompressionService: TiffCompressionService,
    private val imageMagickFallbackService: ImageMagickFallbackService,
    private val svgOptimizeService: SvgOptimizeService,
    private val pdfCompressionService: PdfCompressionService,
) {
    private val logger: Logger = LoggerFactory.getLogger(CompressServiceV2::class.java)

    fun compressImage(
        imageRequestData: ImageRequestData,
        compressionType: CompressionType,
    ): CompressionResult {
        logger.info("Starting optimized compression for ${imageRequestData.originalFormat}")
        logger.info("ImageRequestData - originalFileSize: ${imageRequestData.originalFileSize}, rawBytes: ${imageRequestData.rawBytes?.size}, imageFile: ${imageRequestData.imageFile != null}")

        val startTime = System.currentTimeMillis()

        val compressedBytes = try {
            when (imageRequestData.originalFormat.lowercase()) {
                "png" -> {
                    if (imageRequestData.rawBytes != null) {
                        logger.info("Using rawBytes for PNG compression: ${imageRequestData.rawBytes.size} bytes")
                        fastPngCompressionService.compressPngFromBytes(imageRequestData.rawBytes, compressionType)
                    } else {
                        logger.info("Creating temp file for PNG compression")
                        val tempFile = createTempFile(imageRequestData)
                        logger.info("Temp file created: ${tempFile.length()} bytes")
                        fastPngCompressionService.compressPng(tempFile, compressionType).also {
                            tempFile.delete()
                        }
                    }
                }

                "jpeg", "jpg" -> {
                    if (imageRequestData.rawBytes != null) {
                        logger.info("Using rawBytes for JPEG compression: ${imageRequestData.rawBytes.size} bytes")
                        fastJpegCompressionService.compressJpegFromBytes(imageRequestData.rawBytes, compressionType)
                    } else {
                        logger.info("Creating temp file for JPEG compression")
                        val tempFile = createTempFile(imageRequestData)
                        logger.info("Temp file created: ${tempFile.length()} bytes")
                        fastJpegCompressionService.compressJpegInMemory(tempFile, compressionType).also {
                            tempFile.delete()
                        }
                    }
                }

                "webp" -> {
                    if (imageRequestData.rawBytes != null) {
                        logger.info("Using rawBytes for WebP compression: ${imageRequestData.rawBytes.size} bytes")
                        fastWebPCompressionService.compressWebPFromBytes(imageRequestData.rawBytes, compressionType)
                    } else {
                        logger.info("Creating temp file for WebP compression")
                        val tempFile = createTempFile(imageRequestData)
                        logger.info("Temp file created: ${tempFile.length()} bytes")
                        fastWebPCompressionService.compressWebPInMemory(tempFile, compressionType).also {
                            tempFile.delete()
                        }
                    }
                }

                // Other formats remain the same
                "gif" -> {
                    val rawBytes = imageRequestData.rawBytes
                        ?: throw IllegalArgumentException("Raw bytes required for GIF")
                    gifCompressionService.compressGifUsingGifsicle(rawBytes, compressionType)
                }

                "svg", "svg+xml" -> {
                    val rawBytes = imageRequestData.rawBytes
                        ?: throw IllegalArgumentException("Raw bytes required for SVG")
                    svgOptimizeService.compressSvg(rawBytes)
                }

                "pdf" -> {
                    val tempFile = createTempFile(imageRequestData)
                    pdfCompressionService.compressPdf(tempFile).also { tempFile.delete() }
                }

                "tiff" -> {
                    val tempFile = createTempFile(imageRequestData)
                    tiffCompressionService.compressTiffUsingImageMagick(tempFile).also { tempFile.delete() }
                }

                else -> {
                    logger.warn("Unsupported format: ${imageRequestData.originalFormat}, using fallback")
                    val tempFile = createTempFile(imageRequestData)
                    imageMagickFallbackService.convertAndCompressUsingImageMagick(
                        tempFile, compressionType, imageRequestData.originalFormat
                    ).also { tempFile.delete() }
                }
            }
        } catch (e: Exception) {
            logger.error("Compression failed: ${e.message}")
            imageRequestData.rawBytes ?: throw e
        }

        val duration = System.currentTimeMillis() - startTime
        val compressionPercentage =
            getCompressionPercent(imageRequestData.originalFileSize, compressedBytes.size.toLong())

        logger.info("Final result - Original: ${imageRequestData.originalFileSize} B, Compressed: ${compressedBytes.size} B, Percentage: ${compressionPercentage.toInt()}%")
        logger.info("Optimized compression complete: ${imageRequestData.originalFileSize} B → ${compressedBytes.size} B (${compressionPercentage.toInt()}% reduction, ${duration}ms)")

        return CompressionResult(
            compressedData = compressedBytes,
            originalSize = imageRequestData.originalFileSize,
            compressedSize = compressedBytes.size.toLong(),
            compressionPercentage = compressionPercentage,
        )
    }

    private fun createTempFile(imageRequestData: ImageRequestData): File {
        val tempFile = File.createTempFile("compress", ".${imageRequestData.originalFormat}")

        when {
            imageRequestData.rawBytes != null -> {
                logger.debug("Writing rawBytes to temp file: ${imageRequestData.rawBytes.size} bytes")
                Files.write(tempFile.toPath(), imageRequestData.rawBytes)
            }

            imageRequestData.imageFile != null -> {
                logger.debug("Writing BufferedImage to temp file")
                javax.imageio.ImageIO.write(
                    imageRequestData.imageFile,
                    imageRequestData.originalFormat,
                    tempFile
                )
            }

            else -> {
                tempFile.delete()
                throw IllegalArgumentException("No image data available")
            }
        }

        logger.debug("Temp file created: ${tempFile.length()} bytes")
        return tempFile
    }
}