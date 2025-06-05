package dev.tinify.service.resizeService.resizers

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.UUID

class JpegResizer : ImageResizer {
    private val logger = LoggerFactory.getLogger(JpegResizer::class.java)

    override fun resizeImage(
        imageFile: File,
        originalFileName: String,
        format: String,
        width: Int?,
        height: Int?,
        scale: Double?,
        keepAspectRatio: Boolean,
    ): ByteArray {
        logger.info("Resizing JPEG image")
        val outputFile = File.createTempFile("resized-${UUID.randomUUID()}", ".jpg")

        val command = mutableListOf("convert", imageFile.absolutePath)

        // Preserve color profile by not using -strip and not altering colorspace
        // Remove or adjust -colorspace option
        // command.add("-colorspace")
        // command.add("sRGB") // Remove this line or adjust as shown below

        // If you need to set colorspace metadata without altering pixel data
        // command.add("-set")
        // command.add("colorspace")
        // command.add("sRGB")

        // General settings
        // command.add("-strip") // uncomment this line to remove all profiles

        command.add("-sampling-factor")
        command.add("4:4:4")
        command.add("-interlace")
        command.add("none")
        command.add("-quality")
        command.add("100")

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
        logger.info("JPEG resized successfully")
        return resizedImageBytes
    }
}
