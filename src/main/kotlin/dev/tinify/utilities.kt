package dev.tinify

import org.springframework.http.HttpHeaders
import java.awt.image.BufferedImage
val DOWNLOADS_FOLDER = "downloads"

enum class CompressionType {
    LOSSY,   // More aggressive compression
    LOSSLESS // Less aggressive or no compression (higher quality)
}

enum class Services {
    COMPRESS,
    RESIZE,
    CROP
}

data class ImageProcessingResult(
    val imageBytes: ByteArray,
    val format: String,
    val uniqueFileName: String = ""
)


fun writeImageWithFallback(image: BufferedImage, format: String): ImageProcessingResult {
    val outputStream = java.io.ByteArrayOutputStream()
    return try {
        // Attempt to write in the original format
        writeImage(image, format, outputStream)
        ImageProcessingResult(outputStream.toByteArray(), format)
    } catch (e: Exception) {
        // Log the error and fall back to PNG
        println("Error writing image in format $format: ${e.message}, falling back to PNG")
        outputStream.reset()
        javax.imageio.ImageIO.write(image, "png", outputStream)
        ImageProcessingResult(outputStream.toByteArray(), "png")
    }
}

fun writeImage(image: BufferedImage, format: String, outputStream: java.io.ByteArrayOutputStream) {
    when (format.lowercase()) {
        "tiff" -> {
            val writer = javax.imageio.ImageIO.getImageWritersByFormatName("tiff").next()
            val ios = javax.imageio.ImageIO.createImageOutputStream(outputStream)
            writer.output = ios

            val writeParam = writer.defaultWriteParam
            // Set any necessary write parameters here

            writer.write(null, javax.imageio.IIOImage(image, null, null), writeParam)
            writer.dispose()
            ios.close()
        }

        "webp" -> {
            val writer = javax.imageio.ImageIO.getImageWritersByFormatName("webp").next()
            val ios = javax.imageio.ImageIO.createImageOutputStream(outputStream)
            writer.output = ios

            val writeParam = writer.defaultWriteParam
            // Set any necessary write parameters here

            writer.write(null, javax.imageio.IIOImage(image, null, null), writeParam)
            writer.dispose()
            ios.close()
        }

        else -> {
            javax.imageio.ImageIO.write(image, format, outputStream)
        }
    }
}

val DOMAIN_FULL = "https://tinify.dev"