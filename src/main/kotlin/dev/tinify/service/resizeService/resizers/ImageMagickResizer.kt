package dev.tinify.service.resizeService.resizers

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.*

class ImageMagickResizer : ImageResizer {
    private val logger = LoggerFactory.getLogger(ImageMagickResizer::class.java)

    override fun resizeImage(
        imageFile: File,
        originalFileName: String,
        format: String,
        width: Int?,
        height: Int?,
        scale: Double?,
        keepAspectRatio: Boolean,
    ): ByteArray {
        logger.info("Resizing image with ImageMagick")
        val outputFile = File.createTempFile("resized-${UUID.randomUUID()}", ".$format")

        val command = mutableListOf("convert", imageFile.absolutePath)

        // General settings
        command.add("-strip")
        command.add("-colorspace")
        command.add("sRGB")

        // Resize options
        if (scale != null) {
            command.add("-resize")
            command.add("${(scale * 100).toInt()}%")
        } else {
            val size =
                when {
                    width != null && height != null ->
                        "${width}x${height}${if (!keepAspectRatio) "!" else ""}"

                    width != null -> "${width}x"
                    height != null -> "x${height}"
                    else -> throw IllegalArgumentException("Width or height must be specified")
                }
            command.add("-resize")
            command.add(size)
        }

        // Output file
        command.add(outputFile.absolutePath)

        // Execute ImageMagick command
        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            logger.error("ImageMagick resizing failed")
            val error = process.inputStream.bufferedReader().use { it.readText() }
            throw RuntimeException("ImageMagick resizing failed: $error")
        }

        val resizedImageBytes = Files.readAllBytes(outputFile.toPath())

        // Clean up
        outputFile.delete()
        logger.info("Image resized successfully")
        return resizedImageBytes
    }
}
