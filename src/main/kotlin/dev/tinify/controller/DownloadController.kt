package dev.tinify.controller

import dev.tinify.storage.FileStorageService
import dev.tinify.storage.ImageUtilities
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.File
import java.nio.file.Files

@RestController
@RequestMapping("/api/download")
@CrossOrigin("*")
class DownloadController(
    private val fileStorageService: FileStorageService,
    private val imageUtilities: ImageUtilities,
) {
    private val logger = LoggerFactory.getLogger(DownloadController::class.java)

    @GetMapping("/{incomingFileName:.+}")
    fun downloadImage(
        @PathVariable incomingFileName: String,
        @RequestParam(required = false) inline: Boolean?,
    ): ResponseEntity<ByteArray> {
        logger.debug("\n\n== GET == ")
        logger.debug("Incoming GET request on /api/download/$incomingFileName \nwith inline option $inline")


        try {
            // 1. Strip off any query parameters
            val filename = incomingFileName.split("?")[0]
            logger.debug("Filename without query params: $filename")


            // Step 1: Sanitize and validate the filename
            val sanitizedFilename = fileStorageService.sanitizeFilename(filename)
            logger.debug("Sanitized filename: $sanitizedFilename")

            // 2.5 Check if the filename is valid
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
            val fileBytes = Files.readAllBytes(file.toPath())

            // Step 6: Determine the media type
            val mediaType = imageUtilities.determineMediaType(fileBytes, sanitizedFilename)
            logger.debug("Determined media type: {}", mediaType)


            // 7. Extract the original filename
            val originalFilename = fileStorageService.extractOriginalFilename(sanitizedFilename)
            logger.debug("Original filename: $originalFilename")

            // 8. Prepare response headers
            val headers = HttpHeaders()
            headers.contentType = mediaType
            if (inline == true) {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$originalFilename\"")
            } else {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$originalFilename\"")
            }
            // 9. Return the response with the image bytes and headers
            return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes)
        } catch (e: Exception) {
            logger.error("Error downloading file: ${e.message}", e)
            return ResponseEntity.status(500).body("Error downloading file".toByteArray())
        }
    }

}
