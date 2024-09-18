package dev.tinify.controller

import dev.tinify.storage.FileStorageService
import dev.tinify.storage.ImageUtilities
import org.slf4j.LoggerFactory
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/download")
@CrossOrigin("*")
class DownloadController(
    private val fileStorageService: FileStorageService,
    private val imageUtilities: ImageUtilities,
) {
    private val logger = LoggerFactory.getLogger(DownloadController::class.java)

    @GetMapping("/{filename:.+}")
    fun downloadFile(@PathVariable filename: String): ResponseEntity<ByteArray> {
        logger.debug("Incoming GET request on /api/download/$filename")

        try {
            // Step 1: Sanitize and validate the filename
            val sanitizedFilename = fileStorageService.sanitizeFilename(filename)
            logger.debug("Sanitized filename: $sanitizedFilename")

            if (sanitizedFilename != filename) {
                logger.warn("Invalid filename: $filename")
                return ResponseEntity.badRequest().body("Invalid filename".toByteArray())
            }

            // Step 2: Load the image using FileStorageService
            val (fileBytes, originalFilename) = fileStorageService.loadImage(sanitizedFilename)
            if (fileBytes == null || originalFilename == null) {
                logger.warn("File not found or could not be loaded: $sanitizedFilename")
                return ResponseEntity.status(404).body("File not found".toByteArray())
            }

            // Step 3: Determine the media type
            val mediaType = imageUtilities.determineMediaType(fileBytes, sanitizedFilename)
            logger.debug("Determined media type: {}", mediaType)

            // Step 4: Set the response headers to trigger a download
            val headers = HttpHeaders()
            headers.contentType = mediaType
            headers.contentDisposition = ContentDisposition.builder("attachment")
                .filename(originalFilename, StandardCharsets.UTF_8)
                .build()

            // Step 5: Return the response with the file content and headers
            return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes)
        } catch (e: Exception) {
            logger.error("Error downloading file: ${e.message}", e)
            return ResponseEntity.status(500).body("Error downloading file".toByteArray())
        }
    }

}
