package dev.tinify.service.compressionService.compressors.v2

import dev.tinify.CompressionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

@Service
class FastWebPCompressionService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun compressWebPInMemory(input: File, compressionType: CompressionType): ByteArray {
        val inputBytes = input.readBytes()
        return compressWebPFromBytes(inputBytes, compressionType)
    }

    fun compressWebPFromBytes(inputBytes: ByteArray, compressionType: CompressionType): ByteArray {
        val startTime = System.currentTimeMillis()

        return try {
            val image = ImageIO.read(ByteArrayInputStream(inputBytes))
            val quality = when (compressionType) {
                CompressionType.LOSSY -> 0.80f
                CompressionType.LOSSLESS -> 1.0f
            }

            val result = encodeWebPOptimized(image, quality, compressionType == CompressionType.LOSSLESS)
            val duration = System.currentTimeMillis() - startTime
            log.info("Fast WebP compression: ${inputBytes.size} B → ${result.size} B (${duration}ms)")

            result
        } catch (e: Exception) {
            log.error("Fast WebP compression failed: ${e.message}")
            inputBytes
        }
    }

    private fun encodeWebPOptimized(image: java.awt.image.BufferedImage, quality: Float, lossless: Boolean): ByteArray {
        val baos = ByteArrayOutputStream()

        val writers = ImageIO.getImageWritersByFormatName("webp")
        if (!writers.hasNext()) {
            throw RuntimeException("No WebP writer available")
        }

        val writer = writers.next()
        val writeParam = writer.defaultWriteParam

        if (writeParam.canWriteCompressed()) {
            writeParam.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
            writeParam.compressionQuality = if (lossless) 1.0f else quality
        }

        writer.output = ImageIO.createImageOutputStream(baos)
        writer.write(null, javax.imageio.IIOImage(image, null, null), writeParam)
        writer.dispose()

        return baos.toByteArray()
    }
}