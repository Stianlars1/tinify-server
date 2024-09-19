package dev.tinify.controller

import dev.tinify.Services
import dev.tinify.responses.ImageResponse
import dev.tinify.service.ImageService
import dev.tinify.service.ResizeService
import dev.tinify.service.UsageTrackerService
import dev.tinify.storage.FileStorageService
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
    private val fileStorageService: FileStorageService,
) {
    private val logger = LoggerFactory.getLogger(ResizeController::class.java)

    @PostMapping
    fun resizeImage(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) width: Int?,
        @RequestParam(required = false) height: Int?,
        @RequestParam(required = false) scale: Double?,
        @RequestParam(defaultValue = "true") keepAspectRatio: Boolean,
    ): ResponseEntity<ImageResponse> {
        logger.debug("\n\n== RESIZE == ")
        logger.debug("Incoming POST request on /api/resize")
        logger.debug("width: $width, height: $height, scale: $scale, keepAspectRatio: $keepAspectRatio")
        try {

            // Validate input parameters
            if (scale == null && width == null && height == null) {
                logger.error("You must specify scale, width, or height.")
                return ResponseEntity.badRequest()
                    .body(ImageResponse(isError = true, error = "You must specify scale, width, or height."))
            }

            // Get the image from the request
            val imageRequestData = imageService.getImageFromRequest(file)
            logger.debug("imageRequestData - originalName: ${imageRequestData.originalName}")
            logger.debug("imageRequestData - originalFormat: ${imageRequestData.originalFormat}")
            logger.debug("imageRequestData - originalFileSize: ${imageRequestData.originalFileSize}")

            if (imageRequestData.imageFile == null) {
                logger.error("BufferedImage is null for GIF images")
                return ResponseEntity.badRequest()
                    .body(ImageResponse(isError = true, error = "We do not support resizing GIF images"))

            }
            // Perform resizing
            val resizeResult = resizeService.resizeImage(
                imageFile = imageRequestData.imageFile,
                originalFileName = imageRequestData.originalName,
                format = imageRequestData.originalFormat,
                width = width,
                height = height,
                scale = scale,
                keepAspectRatio = keepAspectRatio
            )

            // Store the compressed image
            val uniqueFileName = fileStorageService.storeImageAndScheduleDeletion(
                resizeResult.imageBytes, imageRequestData.originalName, imageRequestData.originalFormat
            )

            // Generate the download URL
            val downloadUrl = fileStorageService.createDownloadLink(uniqueFileName)


            // Log the count for the usage tracking
            usageTrackerService.incrementServiceCount(Services.RESIZE)

            // Prepare response
            val headers = HttpHeaders()
            headers.contentType = MediaType.parseMediaType("image/${resizeResult.format}")
            headers.set(
                HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${resizeResult.uniqueFileName}\""
            )
            headers.set("X-Unique-Filename", resizeResult.uniqueFileName) // Include unique filename

            val compressPercent = (imageRequestData.originalFileSize / resizeResult.imageBytes.size) * 100

            // Prepare the response
            val responseBody = ImageResponse(
                url = downloadUrl,
                originalFilename = imageRequestData.originalName,
                originalFileSize = imageRequestData.originalFileSize.toString(),
                originalFormat = imageRequestData.originalFormat,
                compressedSize = resizeResult.imageBytes.size.toString(),
                compressionPercentage = compressPercent.toString()
            )
            return ResponseEntity.ok(responseBody)

        } catch (e: Exception) {
            // Handle exceptions
            return ResponseEntity.status(500)
                .body(ImageResponse(isError = true, error = "Error resizing image: ${e.message}"))
        }
    }
}
