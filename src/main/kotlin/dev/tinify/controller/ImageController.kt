package dev.tinify.controller

import dev.tinify.storage.FileStorageService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.util.MimeTypeUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.nio.file.Files


@RestController
@RequestMapping("/api/images")
class ImageController(
    private val fileStorageService: FileStorageService,

    ) {
    private val logger = LoggerFactory.getLogger(ImageController::class.java)

    @GetMapping("/{filename:.+}")
    fun getImage(@PathVariable filename: String): ResponseEntity<ByteArray> {
        logger.debug("\n\n== GET == ")
        logger.debug("Incoming GET request on /api/images/$filename")

        try {
            // 1. Sanitize the filename
            logger.debug("Trying to sanitize filename")
            val sanitizedFilename = fileStorageService.sanitizeFilename(filename)
            logger.debug("Sanitized filename: $sanitizedFilename")
            // 2. Check if the sanitized filename matches the input filename
            if (sanitizedFilename != filename) {
                logger.warn("Invalid filename: $filename")
                return ResponseEntity.badRequest().body("Invalid filename".toByteArray())
            }

            // 3. Construct the path to the file
            val imagePath = fileStorageService.getImagePath(sanitizedFilename)
            logger.debug("Image path: $imagePath")
            val file = File(imagePath)


            // 4. Check if the file exists
            if (!file.exists()) {
                logger.warn("File not found: $filename")
                return ResponseEntity.status(404).body("File not found".toByteArray())
            }

            // 5. Read the file content
            val bytes = Files.readAllBytes(file.toPath())

            // 6. Determine the media type based on the filename
            val mediaType =
                MediaTypeFactory.getMediaType(file.name).orElse(MimeTypeUtils.APPLICATION_OCTET_STREAM as MediaType?)
            logger.debug("Media type: {}", mediaType)
            // 7. Extract the original filename
            val originalFilename = fileStorageService.extractOriginalFilename(sanitizedFilename)
            logger.debug("Original filename: $originalFilename")

            // 8. Prepare response headers
            val headers = HttpHeaders()
            headers.contentType = mediaType
            headers.set(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"$originalFilename\""
            )


            // 9. Return the response with the image bytes and headers
            return ResponseEntity.ok()
                .headers(headers)
                .body(bytes)
        } catch (e: Exception) {
            logger.error("Error retrieving image: ${e.message}", e)
            return ResponseEntity.status(500).body("Error retrieving image".toByteArray())
        }
    }


}
