package dev.tinify.service.compressionService.compressors.v2


import dev.tinify.CompressionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
//import javax.imageio.plugins.png.PNGImageWriteParam

import javax.imageio.stream.MemoryCacheImageOutputStream

@Service
class FastPngCompressionService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun compressPng(input: File, mode: CompressionType): ByteArray {
        val inputBytes = input.readBytes()
        return compressPngFromBytes(inputBytes, mode)
    }

    fun compressPngFromBytes(inputBytes: ByteArray, mode: CompressionType): ByteArray {
        val startTime = System.currentTimeMillis()

        return try {
            val image = ImageIO.read(ByteArrayInputStream(inputBytes))

            val result = when (mode) {
                CompressionType.LOSSY -> compressLossyInMemory(image, inputBytes.size)
                CompressionType.LOSSLESS -> compressLosslessInMemory(image)
            }

            val duration = System.currentTimeMillis() - startTime
            log.info("Fast PNG compression: ${inputBytes.size} B → ${result.size} B (${duration}ms)")

            result
        } catch (e: Exception) {
            log.error("Fast compression failed, using original: ${e.message}")
            inputBytes
        }
    }

    private fun compressLossyInMemory(image: BufferedImage, originalSize: Int): ByteArray {
        // Determine optimal color count based on file size
        val maxColors = when {
            originalSize > 1_500_000 -> 128  // Large files: aggressive quantization
            originalSize > 600_000 -> 192   // Medium files: balanced
            else -> 256                      // Small files: high quality
        }

        // Fast color quantization
        val quantized = quantizeColors(image, maxColors)

        // Encode with optimized PNG parameters
        return encodePngOptimized(quantized, compressionLevel = 6)
    }

    private fun compressLosslessInMemory(image: BufferedImage): ByteArray {
        return encodePngOptimized(image, compressionLevel = 9)
    }

    private fun quantizeColors(image: BufferedImage, maxColors: Int): BufferedImage {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)

        // Fast median cut quantization
        val palette = medianCutQuantization(pixels, maxColors)
        val quantizedPixels = applyPalette(pixels, palette)

        // Create indexed color model
        val r = ByteArray(palette.size)
        val g = ByteArray(palette.size)
        val b = ByteArray(palette.size)
        val a = ByteArray(palette.size)

        palette.forEachIndexed { i, color ->
            a[i] = ((color shr 24) and 0xFF).toByte()
            r[i] = ((color shr 16) and 0xFF).toByte()
            g[i] = ((color shr 8) and 0xFF).toByte()
            b[i] = (color and 0xFF).toByte()
        }

        val colorModel = IndexColorModel(8, palette.size, r, g, b, a)
        val result = BufferedImage(colorModel, colorModel.createCompatibleWritableRaster(width, height), false, null)

        val raster = result.raster
        for (y in 0 until height) {
            for (x in 0 until width) {
                raster.setSample(x, y, 0, quantizedPixels[y * width + x])
            }
        }

        return result
    }

    private fun medianCutQuantization(pixels: IntArray, maxColors: Int): List<Int> {
        // Simplified median cut - collect unique colors first
        val uniqueColors = pixels.toSet().toList()

        return if (uniqueColors.size <= maxColors) {
            uniqueColors
        } else {
            // Sample representative colors using uniform distribution
            val step = uniqueColors.size / maxColors
            (0 until maxColors).map { i -> uniqueColors[i * step] }
        }
    }

    private fun applyPalette(pixels: IntArray, palette: List<Int>): IntArray {
        return pixels.map { pixel ->
            var bestIndex = 0
            var bestDistance = Int.MAX_VALUE

            palette.forEachIndexed { index, paletteColor ->
                val distance = colorDistance(pixel, paletteColor)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }
            bestIndex
        }.toIntArray()
    }

    private fun colorDistance(color1: Int, color2: Int): Int {
        val r1 = (color1 shr 16) and 0xFF
        val g1 = (color1 shr 8) and 0xFF
        val b1 = color1 and 0xFF
        val r2 = (color2 shr 16) and 0xFF
        val g2 = (color2 shr 8) and 0xFF
        val b2 = color2 and 0xFF

        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2

        return dr * dr + dg * dg + db * db
    }

    private fun encodePngOptimized(image: BufferedImage, compressionLevel: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        val ios = MemoryCacheImageOutputStream(baos)

        val writer = ImageIO.getImageWritersByFormatName("png").next()
        writer.output = ios

        val writeParam = writer.defaultWriteParam
        writeParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
        writeParam.compressionQuality = (10 - compressionLevel) / 10.0f

        writer.write(null, IIOImage(image, null, null), writeParam)
        writer.dispose()
        ios.close()

        return baos.toByteArray()
    }
}