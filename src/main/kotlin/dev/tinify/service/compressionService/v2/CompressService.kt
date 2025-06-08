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
        val startTime = System.currentTimeMillis()

        val compressedBytes = try {
            when (imageRequestData.originalFormat.lowercase()) {
                "png" -> {
                    // Prioritize raw bytes for memory efficiency
                    if (imageRequestData.rawBytes != null) {
                        fastPngCompressionService.compressPngFromBytes(imageRequestData.rawBytes, compressionType)
                    } else {
                        val tempFile = createTempFile(imageRequestData)
                        fastPngCompressionService.compressPng(tempFile, compressionType).also {
                            tempFile.delete()
                        }
                    }
                }

                "jpeg", "jpg" -> {
                    if (imageRequestData.rawBytes != null) {
                        fastJpegCompressionService.compressJpegFromBytes(imageRequestData.rawBytes, compressionType)
                    } else {
                        val tempFile = createTempFile(imageRequestData)
                        fastJpegCompressionService.compressJpegInMemory(tempFile, compressionType).also {
                            tempFile.delete()
                        }
                    }
                }

                "webp" -> {
                    if (imageRequestData.rawBytes != null) {
                        fastWebPCompressionService.compressWebPFromBytes(imageRequestData.rawBytes, compressionType)
                    } else {
                        val tempFile = createTempFile(imageRequestData)
                        fastWebPCompressionService.compressWebPInMemory(tempFile, compressionType).also {
                            tempFile.delete()
                        }
                    }
                }

                // Fallback to existing services for complex formats
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
            // Return original bytes if available, otherwise re-throw
            imageRequestData.rawBytes ?: throw e
        }

        val duration = System.currentTimeMillis() - startTime
        val compressionPercentage =
            getCompressionPercent(imageRequestData.originalFileSize, compressedBytes.size.toLong())

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
                Files.write(tempFile.toPath(), imageRequestData.rawBytes)
            }

            imageRequestData.imageFile != null -> {
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

        return tempFile
    }
}