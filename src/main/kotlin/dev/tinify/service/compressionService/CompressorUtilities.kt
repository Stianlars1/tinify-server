package dev.tinify.service.compressionService

import java.io.File
import java.util.*

fun createTempFileWithUniqueName(originalFileName: String, format: String): File {
    val sanitizedFileName =
        originalFileName.substringBeforeLast('.').replace("[^a-zA-Z0-9-_\\.]", "_")
    val uniqueFileName = "input-${sanitizedFileName}-${UUID.randomUUID()}.$format"
    return File.createTempFile(uniqueFileName, null)
}
