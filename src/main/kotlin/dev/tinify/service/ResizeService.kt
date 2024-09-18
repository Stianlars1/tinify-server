package dev.tinify.service

import dev.tinify.ImageProcessingResult
import dev.tinify.storage.FileStorageService
import dev.tinify.writeImageWithFallback
import org.springframework.stereotype.Service
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

@Service
class ResizeService(private val fileStorageService: FileStorageService) {

    fun resizeImage(
        imageFile: BufferedImage,
        originalFileName: String,
        format: String,
        width: Int?,
        height: Int?,
        scale: Double?,
        keepAspectRatio: Boolean,
    ): ImageProcessingResult {
        // Determine new dimensions
        val originalWidth = imageFile.width
        val originalHeight = imageFile.height
        var newWidth = width
        var newHeight = height

        if (scale != null) {
            // Resize based on scale factor
            newWidth = (originalWidth * scale).toInt()
            newHeight = (originalHeight * scale).toInt()
        } else {
            // Resize based on width and/or height
            if (keepAspectRatio) {
                if (newWidth == null && newHeight != null) {
                    // Calculate width to maintain aspect ratio
                    newWidth = (newHeight * originalWidth) / originalHeight
                } else if (newHeight == null && newWidth != null) {
                    // Calculate height to maintain aspect ratio
                    newHeight = (newWidth * originalHeight) / originalWidth
                } else if (newWidth == null && newHeight == null) {
                    // If both are null, use original dimensions
                    newWidth = originalWidth
                    newHeight = originalHeight
                }
            } else {
                // If aspect ratio is not to be maintained, default missing dimensions to original
                if (newWidth == null) newWidth = originalWidth
                if (newHeight == null) newHeight = originalHeight
            }
        }

        // Perform the actual resizing
        val resizedImage = BufferedImage(newWidth!!, newHeight!!, imageFile.type)
        val graphics2D: Graphics2D = resizedImage.createGraphics()
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics2D.drawImage(imageFile, 0, 0, newWidth, newHeight, null)
        graphics2D.dispose()

        // Handle format-specific writing
        val result = writeImageWithFallback(resizedImage, format)


        // Store the image and get unique filename
        val uniqueFileName = fileStorageService.storeImageAndScheduleDeletion(
            result.imageBytes,
            originalFileName,
            result.format
        )

        return result.copy(uniqueFileName = uniqueFileName)

    }

}
