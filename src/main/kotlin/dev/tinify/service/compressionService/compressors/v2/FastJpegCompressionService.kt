package dev.tinify.service.compressionService.compressors.v2

import dev.tinify.CompressionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.plugins.jpeg.JPEGImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

@Service
class FastJpegCompressionService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun compressJpegInMemory(input: File, compressionType: CompressionType): ByteArray {
        val inputBytes = input.readBytes()
        return compressJpegFromBytes(inputBytes, compressionType)
    }

    fun compressJpegFromBytes(inputBytes: ByteArray, compressionType: CompressionType): ByteArray {
        val startTime = System.currentTimeMillis()

        return try {
            val image = ImageIO.read(ByteArrayInputStream(inputBytes))
            val quality = when (compressionType) {
                CompressionType.LOSSY -> 0.85f
                CompressionType.LOSSLESS -> 0.95f
            }

            val result = encodeJpegOptimized(image, quality)
            val duration = System.currentTimeMillis() - startTime
            log.info("Fast JPEG compression: ${inputBytes.size} B → ${result.size} B (${duration}ms)")

            result
        } catch (e: Exception) {
            log.error("Fast JPEG compression failed: ${e.message}")
            inputBytes
        }
    }

    private fun encodeJpegOptimized(image: BufferedImage, quality: Float): ByteArray {
        val baos = ByteArrayOutputStream()
        val ios = MemoryCacheImageOutputStream(baos)

        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        writer.output = ios

        val writeParam = writer.defaultWriteParam as JPEGImageWriteParam
        writeParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
        writeParam.compressionQuality = quality
        writeParam.progressiveMode = JPEGImageWriteParam.MODE_DEFAULT
        writeParam.optimizeHuffmanTables = true

        writer.write(null, IIOImage(image, null, null), writeParam)
        writer.dispose()
        ios.close()

        return baos.toByteArray()
    }
}