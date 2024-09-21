package dev.tinify.service.compressionService

import java.io.File
import java.util.*

fun createTempFileWithUniqueName(originalFileName: String, format: String): File {
    // Sanitize the original file name by replacing non-alphanumeric characters with underscores
    val sanitizedFileName = originalFileName.substringBeforeLast('.').replace("[^a-zA-Z0-9-_]", "_")

    // Create a unique file name by appending a UUID before the file extension
    val uniqueFileName = "input-${sanitizedFileName}-${UUID.randomUUID()}.$format"

    // Create the temp file with the unique file name in the system's temp directory
    return File.createTempFile(uniqueFileName, null, null)
}
