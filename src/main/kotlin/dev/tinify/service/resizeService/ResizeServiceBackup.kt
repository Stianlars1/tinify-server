/*
package dev.tinify.service

import dev.tinify.storage.FileStorageService
import java.io.File
import java.nio.file.Files
import java.util.*
import org.springframework.stereotype.Service

@Service
class ResizeService(private val fileStorageService: FileStorageService) {

    fun resizeImage(
        imageFile: File, // Instead of BufferedImage, we pass the image as a File for ImageMagick
        // processing
        originalFileName: String,
        format: String,
        width: Int?,
        height: Int?,
        scale: Double?,
        keepAspectRatio: Boolean,
    ): ByteArray {

        val outputFile = File.createTempFile("resized-${UUID.randomUUID()}", ".$format")

        // Prepare ImageMagick command
        val command = mutableListOf("convert", imageFile.absolutePath)

        // Add resize options
        if (scale != null) {
            // If scale is provided, calculate scaling factor
            command.add("-resize")
            command.add("${(scale * 100).toInt()}%") // Converts scale to percentage for ImageMagick
        } else {
            // If width/height is provided, adjust the size
            if (width != null || height != null) {
                val size =
                    if (keepAspectRatio) {
                        "${width ?: ""}x${height ?: ""}" // Respects aspect ratio
                    } else {
                        "${width ?: ""}x${height ?: ""}!" // Ignore aspect ratio with "!"
                    }
                command.add("-resize")
                command.add(size)
            }
        }

        // Output the resized image
        command.add(outputFile.absolutePath)

        // Execute ImageMagick command
        val process = ProcessBuilder(command).start()
        process.waitFor()

        if (process.exitValue() != 0) {
            throw RuntimeException(
                "ImageMagick resizing failed: ${
                    process.errorStream.bufferedReader().readText()
                }"
            )
        }

        // Read the resized image as bytes
        val resizedImageBytes = Files.readAllBytes(outputFile.toPath())

        return resizedImageBytes
    }
}
*/
