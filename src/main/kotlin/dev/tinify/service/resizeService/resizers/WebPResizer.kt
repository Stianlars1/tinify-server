package dev.tinify.service.resizeService.resizers

import java.io.File
import java.nio.file.Files
import java.util.*
import org.slf4j.LoggerFactory

class WebPResizer : ImageResizer {
    private val logger = LoggerFactory.getLogger(WebPResizer::class.java)

    override fun resizeImage(
        imageFile: File,
        originalFileName: String,
        format: String,
        width: Int?,
        height: Int?,
        scale: Double?,
        keepAspectRatio: Boolean,
    ): ByteArray {

        val decodedFile = File.createTempFile("decoded-${UUID.randomUUID()}", ".png")
        val outputFile = File.createTempFile("resized-${UUID.randomUUID()}", ".webp")
        logger.info("Resizing WebP image")
        try {
            // Step 1: Decode WebP to PNG
            val decodeCommand =
                listOf("dwebp", imageFile.absolutePath, "-o", decodedFile.absolutePath)
            val decodeProcess = ProcessBuilder(decodeCommand).redirectErrorStream(true).start()
            if (decodeProcess.waitFor() != 0) {
                val error = decodeProcess.inputStream.bufferedReader().use { it.readText() }
                logger.error("WebP decoding failed")
                throw RuntimeException("dwebp decoding failed: $error")
            }

            // Step 2: Resize and encode back to WebP
            val encodeCommand = mutableListOf("cwebp", decodedFile.absolutePath)

            // Resize options
            if (scale != null) {
                encodeCommand.add("-resize")
                encodeCommand.add("0")
                encodeCommand.add("0")
                encodeCommand.add("-scale")
                encodeCommand.add(scale.toString())
            } else if (width != null || height != null) {
                encodeCommand.add("-resize")
                encodeCommand.add(width?.toString() ?: "0")
                encodeCommand.add(height?.toString() ?: "0")
            } else {
                logger.error("Width or height or scale must be specified")
                throw IllegalArgumentException("Width or height or scale must be specified")
            }

            // Output file
            encodeCommand.add("-o")
            encodeCommand.add(outputFile.absolutePath)

            val encodeProcess = ProcessBuilder(encodeCommand).redirectErrorStream(true).start()
            if (encodeProcess.waitFor() != 0) {
                logger.error("WebP encoding failed")
                val error = encodeProcess.inputStream.bufferedReader().use { it.readText() }
                throw RuntimeException("cwebp encoding failed: $error")
            }

            val resizedImageBytes = Files.readAllBytes(outputFile.toPath())

            // Clean up
            decodedFile.delete()
            outputFile.delete()

            return resizedImageBytes
        } catch (e: Exception) {
            logger.error("WebP resizing failed", e)
            // Clean up in case of exception
            decodedFile.delete()
            outputFile.delete()
            throw e
        }
    }
}
