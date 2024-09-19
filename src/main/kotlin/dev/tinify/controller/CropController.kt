package dev.tinify.controller

import dev.tinify.Services
import dev.tinify.service.CropService
import dev.tinify.service.ImageService
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
@RequestMapping("/api/crop")
class CropController(
    private val imageService: ImageService,
    private val cropService: CropService,
    private val usageTrackerService: UsageTrackerService,
) {
    private val logger = LoggerFactory.getLogger(CropController::class.java)

    @PostMapping
    fun cropImage(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("x") x: Int,
        @RequestParam("y") y: Int,
        @RequestParam("width") width: Int,
        @RequestParam("height") height: Int,
    ): ResponseEntity<ByteArray> {
        logger.debug("\n\n== CROP == ")
        logger.debug("Incoming POST request on /api/crop")
        logger.debug("x: $x, y: $y, width: $width, height: $height")
        try {
            // Get the image from the request
            val imageRequestData = imageService.getImageFromRequest(file)
            logger.debug("Image data: {}", imageRequestData)
            if (imageRequestData.imageFile == null) {
                logger.error("BufferedImage is null for non-GIF image")
                return ResponseEntity.badRequest().body("BufferedImage is null for non-GIF image".toByteArray())
            }

            // Perform cropping
            val result = cropService.cropImage(
                imageRequestData,
                x = x,
                y = y,
                width = width,
                height = height
            )
            // Log the usage
            usageTrackerService.incrementServiceCount(Services.CROP)

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
            logger.error("Error cropping image: ${e.message}")
            return ResponseEntity.status(500).body("Error cropping image: ${e.message}".toByteArray())
        }
    }
}
