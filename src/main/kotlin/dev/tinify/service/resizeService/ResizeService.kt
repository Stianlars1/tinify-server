package dev.tinify.service.resizeService

import dev.tinify.service.resizeService.resizers.GifResizer
import dev.tinify.service.resizeService.resizers.ImageMagickResizer
import dev.tinify.service.resizeService.resizers.JpegResizer
import dev.tinify.service.resizeService.resizers.PngResizer
import dev.tinify.service.resizeService.resizers.WebPResizer
import java.io.File
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ResizeService {
    private val logger = LoggerFactory.getLogger(ResizeService::class.java)
    private val pngResizer = PngResizer()
    private val jpegResizer = JpegResizer()
    private val gifResizer = GifResizer()
    private val webpResizer = WebPResizer()
    private val fallbackResizer = ImageMagickResizer()

    fun resizeImage(
        imageFile: File,
        originalFileName: String,
        format: String,
        width: Int?,
        height: Int?,
        scale: Double?,
        keepAspectRatio: Boolean,
    ): ByteArray {
        logger.info("== Resizing image ==  incoming format: $format")

        return try {
            when (format.lowercase()) {
                "png" ->
                    pngResizer.resizeImage(
                        imageFile,
                        originalFileName,
                        format,
                        width,
                        height,
                        scale,
                        keepAspectRatio,
                    )
                "jpeg",
                "jpg" ->
                    jpegResizer.resizeImage(
                        imageFile,
                        originalFileName,
                        format,
                        width,
                        height,
                        scale,
                        keepAspectRatio,
                    )
                "gif" ->
                    gifResizer.resizeImage(
                        imageFile,
                        originalFileName,
                        format,
                        width,
                        height,
                        scale,
                        keepAspectRatio,
                    )
                "webp" ->
                    webpResizer.resizeImage(
                        imageFile,
                        originalFileName,
                        format,
                        width,
                        height,
                        scale,
                        keepAspectRatio,
                    )
                else ->
                    fallbackResizer.resizeImage(
                        imageFile,
                        originalFileName,
                        format,
                        width,
                        height,
                        scale,
                        keepAspectRatio,
                    )
            }
        } catch (e: Exception) {
            // Fallback to ImageMagick
            logger.error("Error resizing image with $format, falling back to ImageMagick", e)
            fallbackResizer.resizeImage(
                imageFile,
                originalFileName,
                format,
                width,
                height,
                scale,
                keepAspectRatio,
            )
        }
    }
}
