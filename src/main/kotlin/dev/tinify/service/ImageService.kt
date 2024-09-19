package dev.tinify.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.imageio.ImageIO

data class ImageRequestData(
    val originalFormat: String,
    val originalName: String,
    val imageFile: BufferedImage?,
    val rawBytes: ByteArray?,
    val originalFileSize: Long,
)

data class ImageInfo(
    val originalFormat: String,
    val originalName: String,
    val originalFileSize: Long,
    val rawBytes: ByteArray?,
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
            throw IllegalArgumentException(
                "getImageFromRequest: Unsupported image format: $contentType"
            )
        }

        val originalName = file.originalFilename ?: "image.${contentType.split("/").last()}"
        logger.debug("Original name: $originalName")
        val originalFormat = contentType.split("/").last().lowercase()
        logger.debug("Original format: $originalFormat")

        val originalFileSize = file.size
        logger.debug("Original file size: $originalFileSize")

        val rawBytes = file.bytes

        return if (originalFormat == "gif" && isAnimatedGif(rawBytes)) {
            logger.debug("Detected animated GIF")
            ImageRequestData(
                originalFormat = originalFormat,
                originalName = originalName,
                imageFile = null, // No BufferedImage for animated GIF
                rawBytes = rawBytes,
                originalFileSize = originalFileSize,
            )
        } else if (originalFormat == "svg+xml") {
            logger.debug("Detected SVG file, skipping ImageIO")
            return ImageRequestData(
                originalFormat = originalFormat,
                originalName = originalName,
                imageFile = null,
                rawBytes = rawBytes,
                originalFileSize = originalFileSize,
            )
        } else {
            // Try to process the image as a static image using ImageIO
            try {
                val imageFile = readImageWithImageIO(rawBytes)
                logger.debug("Successfully processed as static image with ImageIO")
                ImageRequestData(
                    originalFormat = originalFormat,
                    originalName = originalName,
                    imageFile = imageFile,
                    rawBytes = null,
                    originalFileSize = originalFileSize,
                )
            } catch (e: Exception) {
                logger.warn("ImageIO failed to read the image, attempting with ImageMagick...")
                // If ImageIO fails (likely due to unsupported format), use ImageMagick
                val convertedFile = useImageMagickFallback(rawBytes, originalFormat)
                logger.debug("Processed with ImageMagick fallback")
                ImageRequestData(
                    originalFormat = originalFormat,
                    originalName = originalName,
                    imageFile = null, // No BufferedImage for ImageMagick, just raw bytes
                    rawBytes =
                        Files.readAllBytes(
                            convertedFile.toPath()
                        ), // Read the raw bytes from the converted file
                    originalFileSize = originalFileSize,
                )
            }
        }
    }

    @Throws(Exception::class)
    fun getImageInfoFromRequest(file: MultipartFile): ImageInfo {
        logger.debug("\n\n== service ==")
        logger.debug("= getImageInfoFromRequest =")
        val contentType = file.contentType
        logger.debug("Content type: $contentType")

        if (contentType == null || !isSupportedContentType(contentType)) {
            logger.error("Unsupported image format: $contentType")
            throw IllegalArgumentException(
                "getImageFromRequest: Unsupported image format: $contentType"
            )
        }

        val originalName =
            file.originalFilename ?: "image-${UUID.randomUUID()}.${contentType.split("/").last()}"
        logger.debug("Original name: $originalName")
        val originalFormat = contentType.split("/").last().lowercase()
        logger.debug("Original format: $originalFormat")

        val originalFileSize = file.size
        logger.debug("Original file size: $originalFileSize")

        return ImageInfo(
            originalFormat = originalFormat,
            originalName = originalName,
            rawBytes = file.bytes,
            originalFileSize = originalFileSize,
        )
    }

    // Fallback to ImageMagick if ImageIO fails
    private fun useImageMagickFallback(rawBytes: ByteArray, format: String): File {
        val tempInputFile = File.createTempFile("input-${UUID.randomUUID()}", ".$format")
        Files.write(tempInputFile.toPath(), rawBytes)

        val tempOutputFile = File.createTempFile("output-${UUID.randomUUID()}", ".$format")

        try {
            // Use ImageMagick to convert the image to its original format or a different format if
            // necessary
            val command = listOf("convert", tempInputFile.absolutePath, tempOutputFile.absolutePath)
            val process = ProcessBuilder(command).start()
            process.waitFor()

            if (process.exitValue() != 0) {
                throw RuntimeException(
                    "ImageMagick conversion failed: ${
                        process.errorStream.bufferedReader().readText()
                    }"
                )
            }
            return tempOutputFile
        } catch (e: Exception) {
            throw RuntimeException("Failed to process image with ImageMagick", e)
        } finally {
            tempInputFile.delete()
        }
    }

    private fun readImageWithImageIO(rawBytes: ByteArray): BufferedImage {
        val inputStream = ByteArrayInputStream(rawBytes)
        return ImageIO.read(inputStream)
            ?: throw IllegalArgumentException("Failed to read image with ImageIO")
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
        val format = contentType.split("/").last().lowercase()

        // Add your known formats as well as the formats supported by ImageMagick
        return format in listOf("jpeg", "jpg", "png", "gif", "tiff", "webp", "svg+xml") ||
            supportedformatsImageMagick.contains(format.uppercase())
    }
}

val supportedformatsImageMagick =
    listOf(
        "AAI",
        "APNG",
        "ART",
        "ARW",
        "AVI",
        "AVIF",
        "AVS",
        "BAYER",
        "BPG",
        "BMP",
        "BRF",
        "CALS",
        "CIN",
        "CIP",
        "CMYK",
        "CMYKA",
        "CR2",
        "CRW",
        "CUBE",
        "CUR",
        "CUT",
        "DCM",
        "DCR",
        "DCX",
        "DDS",
        "DEBUG",
        "DIB",
        "DJVU",
        "DMR",
        "DNG",
        "DOT",
        "DPX",
        "EMF",
        "EPDF",
        "EPI",
        "EPS",
        "EPS2",
        "EPS3",
        "EPSF",
        "EPSI",
        "EPT",
        "EXR",
        "FARBFELD",
        "FAX",
        "FITS",
        "FL32",
        "FLIF",
        "FPX",
        "FTXT",
        "GIF",
        "GPLT",
        "GRAY",
        "GRAYA",
        "HDR",
        "HDR",
        "HEIC",
        "HPGL",
        "HRZ",
        "HTML",
        "ICO",
        "INFO",
        "ISOBRL",
        "ISOBRL6",
        "JBIG",
        "JNG",
        "JP2",
        "JPT",
        "J2C",
        "J2K",
        "JPEG",
        "JXR",
        "JSON",
        "JXL",
        "KERNEL",
        "MAN",
        "MAT",
        "MIFF",
        "MONO",
        "MNG",
        "M2V",
        "MPEG",
        "MPC",
        "MPO",
        "MPR",
        "MRW",
        "MSL",
        "MTV",
        "MVG",
        "NEF",
        "ORF",
        "ORA",
        "OTB",
        "P7",
        "PALM",
        "PAM",
        "PBM",
        "PCD",
        "PCDS",
        "PCL",
        "PCX",
        "PDB",
        "PDF",
        "PEF",
        "PES",
        "PFA",
        "PFB",
        "PFM",
        "PGM",
        "PHM",
        "PICON",
        "PICT",
        "PIX",
        "PNG",
        "PNG8",
        "PNG00",
        "PNG24",
        "PNG32",
        "PNG48",
        "PNG64",
        "PNM",
        "POCKETMOD",
        "PPM",
        "PS",
        "PS2",
        "PS3",
        "PSB",
        "PSD",
        "PTIF",
        "PWP",
        "QOI",
        "RAD",
        "RAF",
        "RAW",
        "RGB",
        "RGB565",
        "RGBA",
        "RGF",
        "RLA",
        "RLE",
        "SCT",
        "SFW",
        "SGI",
        "SHTML",
        "SID",
        "SPARSE",
        "STRIMG",
        "SUN",
        "SVG",
        "TEXT",
        "TGA",
        "TIFF",
        "TIM",
        "TTF",
        "TXT",
        "UBRL",
        "UBRL6",
        "UHDR",
        "UIL",
        "UYVY",
        "VICAR",
        "VIDEO",
        "VIFF",
        "WBMP",
        "WDP",
        "WEBP",
        "WMF",
        "WPG",
        "X",
        "XBM",
        "XCF",
        "XPM",
        "XWD",
        "X3F",
        "YAML",
        "YCbCr",
        "YCbCrA",
        "YUV",
    )
