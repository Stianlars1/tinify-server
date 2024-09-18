package dev.tinify.controller

import dev.tinify.Services
import dev.tinify.service.ImageService
import dev.tinify.service.ResizeService
import dev.tinify.service.UsageTrackerService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/resize")
class ResizeController(
    private val imageService: ImageService,
    private val resizeService: ResizeService,
    private val usageTrackerService: UsageTrackerService,
) {
    private val logger = LoggerFactory.getLogger(ResizeController::class.java)

    @PostMapping
    fun resizeImage(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) width: Int?,
        @RequestParam(required = false) height: Int?,
        @RequestParam(required = false) scale: Double?,
        @RequestParam(defaultValue = "true") keepAspectRatio: Boolean,
    ): ResponseEntity<ByteArray> {
        logger.debug("\n\n== RESIZE == ")
        logger.debug("Incoming POST request on /api/resize")
        logger.debug("width: $width, height: $height, scale: $scale, keepAspectRatio: $keepAspectRatio")
        try {
            // Validate input parameters
            if (scale == null && width == null && height == null) {
                logger.error("You must specify scale, width, or height.")
                return ResponseEntity.badRequest().body("You must specify scale, width, or height.".toByteArray())
            }

            // Get the image from the request
            val imageRequestData = imageService.getImageFromRequest(file)
            logger.debug("imageRequestData - originalName: ${imageRequestData.originalName}")
            logger.debug("imageRequestData - originalFormat: ${imageRequestData.originalFormat}")
            logger.debug("imageRequestData - originalFileSize: ${imageRequestData.originalFileSize}")

            // Perform resizing
            val result = resizeService.resizeImage(
                imageFile = imageRequestData.imageFile,
                originalFileName = imageRequestData.originalName,
                format = imageRequestData.originalFormat,
                width = width,
                height = height,
                scale = scale,
                keepAspectRatio = keepAspectRatio
            )

            // Log the count for the usage tracking
            usageTrackerService.incrementServiceCount(Services.RESIZE)

            // Prepare response
            val headers = HttpHeaders()
            headers.contentType = MediaType.parseMediaType("image/${result.format}")
            headers.set(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"${result.uniqueFileName}\""
            )
            headers.set("X-Unique-Filename", result.uniqueFileName) // Include unique filename

            return ResponseEntity.ok()
                .headers(headers)
                .body(result.imageBytes)
        } catch (e: Exception) {
            // Handle exceptions
            return ResponseEntity.status(500).body("Error resizing image: ${e.message}".toByteArray())
        }
    }
}
