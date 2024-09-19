package dev.tinify.controller

import dev.tinify.Services
import dev.tinify.getCompressionPercent
import dev.tinify.responses.ImageResponse
import dev.tinify.responses.createCustomHeaders
import dev.tinify.service.CropService
import dev.tinify.service.ImageService
import dev.tinify.service.UsageTrackerService
import dev.tinify.storage.FileStorageService
import dev.tinify.storage.ImageUtilities
import org.slf4j.LoggerFactory
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
    private val fileStorageService: FileStorageService,
    private val imageUtilities: ImageUtilities,
) {
    private val logger = LoggerFactory.getLogger(CropController::class.java)

    @PostMapping
    fun cropImage(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("x") x: Int,
        @RequestParam("y") y: Int,
        @RequestParam("width") width: Int,
        @RequestParam("height") height: Int,
    ): ResponseEntity<ImageResponse> {
        logger.debug("\n\n== CROP == ")
        logger.debug("Incoming POST request on /api/crop")
        logger.debug("x: $x, y: $y, width: $width, height: $height")
        try {
            // Get the image from the request
            val imageRequestData = imageService.getImageFromRequest(file)
            logger.debug("Image data: {}", imageRequestData)
            if (imageRequestData.imageFile == null) {
                logger.error("BufferedImage is null for GIF image")
                return ResponseEntity.badRequest()
                    .body(ImageResponse(isError = true, error = "We do not support cropping GIF images"))
            }

            // Perform cropping
            val cropResult = cropService.cropImage(
                imageRequestData, x = x, y = y, width = width, height = height
            )

            // Store the compressed image
            val uniqueFileName = fileStorageService.storeImageAndScheduleDeletion(
                cropResult.imageBytes, imageRequestData.originalName, imageRequestData.originalFormat
            )

            // Generate the download URL
            val downloadUrl = fileStorageService.createDownloadLink(uniqueFileName)


            // Log the usage
            usageTrackerService.incrementServiceCount(Services.CROP)


            val compressPercent =
                getCompressionPercent(imageRequestData.originalFileSize, cropResult.imageBytes.size.toLong())

            // Prepare the response
            val responseBody = ImageResponse(
                url = downloadUrl,
                originalFilename = imageRequestData.originalName,
                originalFileSize = imageRequestData.originalFileSize.toString(),
                originalFormat = imageRequestData.originalFormat,
                compressedSize = cropResult.imageBytes.size.toString(),
                compressionPercentage = compressPercent.toString()
            )
            // Prepare the headers
            val headers = createCustomHeaders(
                originalFilename = imageRequestData.originalName,
                originalFileSize = imageRequestData.originalFileSize.toString(),
                originalFormat = imageRequestData.originalFormat,
                compressedSize = cropResult.imageBytes.size.toString(),
                compressionPercentage = compressPercent.toString(),
                uniqueFilename = cropResult.uniqueFileName,
                customContentType = imageUtilities.determineMediaType(cropResult.imageBytes,imageRequestData.originalName),
                contentType = MediaType.APPLICATION_JSON,
                inline = false,
            )

            return ResponseEntity.ok().headers(headers).body(responseBody)
        } catch (e: Exception) {
            // Handle exceptions
            logger.error("Error cropping image: ${e.message}")
            return ResponseEntity.status(500)
                .body(ImageResponse(isError = true, error = "Error cropping image: ${e.message}"))
        }
    }
}
