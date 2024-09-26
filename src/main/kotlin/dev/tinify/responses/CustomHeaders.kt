package dev.tinify.responses

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

class CustomHeaders

// make all arguments optional to add..
fun createCustomHeaders(
    originalFilename: String? = null,
    originalFileSize: String? = null,
    originalFormat: String? = null,
    compressedSize: String? = null,
    compressionPercentage: String? = null,
    uniqueFilename: String? = null,
    contentType: MediaType? = null,
    customContentType: MediaType? = null,
    contentLength: Long? = null,
    inline: Boolean? = false,
): HttpHeaders {
    val headers = HttpHeaders()
    headers.contentType = contentType // "image/<format>" === mediatype also.
    if (contentLength != null) {
        headers.contentLength = contentLength
    }
    headers.set("X-Original-Filename", originalFilename)
    headers.set("X-Original-File-Size", originalFileSize)
    headers.set("X-Original-Format", originalFormat)
    headers.set("X-Content-Type", customContentType.toString())
    headers.set("X-Compressed-Size", compressedSize)
    headers.set("X-Compression-Percentage", compressionPercentage)
    headers.set("X-Unique-Filename", uniqueFilename)
    if (inline == true) {
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$originalFilename\"")
    } else {
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$originalFilename\"")
    }
    return headers
}
