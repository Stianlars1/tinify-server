package dev.tinify.service.resizeService.resizers

import java.io.File

interface ImageResizer {
    fun resizeImage(
        imageFile: File,
        originalFileName: String,
        format: String,
        width: Int?,
        height: Int?,
        scale: Double?,
        keepAspectRatio: Boolean,
    ): ByteArray
}
