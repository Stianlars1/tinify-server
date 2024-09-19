package dev.tinify.storage

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream
import javax.imageio.ImageIO
import javax.imageio.ImageReader

@Service
class ImageUtilities {
    private val logger = LoggerFactory.getLogger(ImageUtilities::class.java)
    fun getMediaType(file: File): MediaType {
        var reader: ImageReader? = null
        try {
            FileInputStream(file).use { inputStream ->
                ImageIO.createImageInputStream(inputStream).use { imageInputStream ->
                    val readers = ImageIO.getImageReaders(imageInputStream)
                    if (readers.hasNext()) {
                        reader = readers.next()
                        val formatName = reader?.formatName
                        // Map the format name to MediaType
                        return when (formatName?.lowercase()) {
                            "jpeg", "jpg" -> MediaType.IMAGE_JPEG
                            "png" -> MediaType.IMAGE_PNG
                            "gif" -> MediaType.IMAGE_GIF
                            "bmp" -> MediaType.parseMediaType("image/bmp")
                            "webp" -> MediaType.parseMediaType("image/webp")
                            "tiff", "tif" -> MediaType.parseMediaType("image/tiff")
                            else -> MediaType.APPLICATION_OCTET_STREAM
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log the exception if needed
        } finally {
            reader?.dispose()
        }
        return MediaType.APPLICATION_OCTET_STREAM
    }


    // Helper function to determine the media type from byte array
// Helper function to determine the media type from byte array
    fun determineMediaType(fileBytes: ByteArray, filename: String): MediaType {
        // Try to determine the media type from the file extension
        if (filename.endsWith(".svg")) {
            return MediaType.parseMediaType("image/svg+xml") // Explicit return for SVG files
        }

        val mediaTypeFromExtension = MediaTypeFactory.getMediaType(filename).orElse(null)

        if (mediaTypeFromExtension != null && mediaTypeFromExtension != MediaType.APPLICATION_OCTET_STREAM) {
            return mediaTypeFromExtension
        }

        // Fallback to using magic numbers
        return getMediaTypeFromMagicNumbers(fileBytes)
    }


    // Function to determine media type using magic numbers
    private fun getMediaTypeFromMagicNumbers(fileBytes: ByteArray): MediaType {
        try {
            val headerBytes = fileBytes.take(12).toByteArray()
            if (headerBytes.isEmpty()) {
                // Empty file
                return MediaType.APPLICATION_OCTET_STREAM
            }

            // Check for JPEG signature
            if (headerBytes[0] == 0xFF.toByte() && headerBytes[1] == 0xD8.toByte()) {
                return MediaType.IMAGE_JPEG
            }

            // Check for PNG signature
            val pngSignature = byteArrayOf(
                0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
                0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
            )
            if (headerBytes.size >= 8 && headerBytes.sliceArray(0..7).contentEquals(pngSignature)) {
                return MediaType.IMAGE_PNG
            }

            // Check for GIF signature
            if (headerBytes.size >= 6 &&
                headerBytes[0] == 'G'.code.toByte() && headerBytes[1] == 'I'.code.toByte() &&
                headerBytes[2] == 'F'.code.toByte() && headerBytes[3] == '8'.code.toByte() &&
                (headerBytes[4] == '7'.code.toByte() || headerBytes[4] == '9'.code.toByte()) &&
                headerBytes[5] == 'a'.code.toByte()
            ) {
                return MediaType.IMAGE_GIF
            }

            // Check for BMP signature
            if (headerBytes[0] == 0x42.toByte() && headerBytes[1] == 0x4D.toByte()) {
                return MediaType.parseMediaType("image/bmp")
            }

            // Fallback to application/octet-stream
            return MediaType.APPLICATION_OCTET_STREAM
        } catch (e: Exception) {
            logger.error("Error determining media type: ${e.message}", e)
            return MediaType.APPLICATION_OCTET_STREAM
        }
    }
}