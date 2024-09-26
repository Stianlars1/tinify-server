package dev.tinify.service.resizeService.resizers

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.UUID

class GifResizer : ImageResizer {

    private val logger = LoggerFactory.getLogger(GifResizer::class.java)

    override fun resizeImage(
        imageFile: File,
        originalFileName: String,
        format: String,
        width: Int?,
        height: Int?,
        scale: Double?,
        keepAspectRatio: Boolean,
    ): ByteArray {
        val outputFile = File.createTempFile("resized-${UUID.randomUUID()}", ".gif")

        val command = mutableListOf("gifsicle", "--careful", imageFile.absolutePath)

        // Resize options
        if (scale != null) {
            command.add("--scale")
            command.add(scale.toString())
        } else if (width != null || height != null) {
            val resizeOption =
                when {
                    width != null && height != null -> "${width}x${height}"
                    width != null -> "${width}x"
                    height != null -> "x${height}"
                    else -> throw IllegalArgumentException("Width or height must be specified")
                }
            if (keepAspectRatio) {
                command.add("--resize-fit")
            } else {
                command.add("--resize")
            }
            command.add(resizeOption)
        } else {
            logger.error("Width or height or scale must be specified")
            throw IllegalArgumentException("Width or height or scale must be specified")
        }

        // Output file
        command.add("-o")
        command.add(outputFile.absolutePath)

        // Execute gifsicle command
        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            logger.error("gifsicle resizing failed")
            val error = process.inputStream.bufferedReader().use { it.readText() }
            throw RuntimeException("gifsicle resizing failed: $error")
        }

        val resizedImageBytes = Files.readAllBytes(outputFile.toPath())

        // Clean up
        outputFile.delete()
        logger.info("Gif resized successfully")
        return resizedImageBytes
    }
}
