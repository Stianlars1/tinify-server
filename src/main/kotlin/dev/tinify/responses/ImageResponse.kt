package dev.tinify.responses

class ImageResponse(
    val url: String = "",
    val originalFilename: String = "",
    val originalFileSize: String = "",
    val originalFormat: String = "",
    val compressedSize: String = "",
    val compressionPercentage: String = "",
    val isError: Boolean = false,
    val error: String = "",
)