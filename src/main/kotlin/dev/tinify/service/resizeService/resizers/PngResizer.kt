package dev.tinify.service.resizeService.resizers

import java.io.File
import java.nio.file.Files
import java.util.*
import org.slf4j.LoggerFactory

class PngResizer : ImageResizer {
    private val logger = LoggerFactory.getLogger(PngResizer::class.java)

    override fun resizeImage(
        imageFile: File,
        originalFileName: String,
        format: String,
        width: Int?,
        height: Int?,
        scale: Double?,
        keepAspectRatio: Boolean,
    ): ByteArray {
        logger.info("Resizing PNG image")
        val outputFile = File.createTempFile("resized-${UUID.randomUUID()}", ".png")

        val command = mutableListOf("convert", imageFile.absolutePath)

        // Preserve alpha channel
        command.add("-background")
        command.add("none")
        command.add("-alpha")
        command.add("on")

        // General settings
        command.add("-strip")
        command.add("-colorspace")
        command.add("sRGB")

        // PNG-specific adjustments
        command.add("-depth")
        command.add("8")
        command.add("-define")
        command.add("png:color-type=6")

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
        logger.info("PNG resized successfully")
        return resizedImageBytes
    }
}
