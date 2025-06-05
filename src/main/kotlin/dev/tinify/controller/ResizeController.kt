package dev.tinify.controller

import dev.tinify.CompressionType
import dev.tinify.Services
import dev.tinify.getCompressionPercent
import dev.tinify.responses.ImageResponse
import dev.tinify.responses.createCustomHeaders
import dev.tinify.service.CompressService
import dev.tinify.service.ImageRequestData
import dev.tinify.service.ImageService
import dev.tinify.service.UsageTrackerService
import dev.tinify.service.resizeService.ResizeService
import dev.tinify.storage.FileStorageService
import dev.tinify.storage.ImageUtilities
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File

@RestController
@RequestMapping("/api/resize")
@CrossOrigin("*")
class ResizeController(
    private val imageService: ImageService,
    private val resizeService: ResizeService,
    private val usageTrackerService: UsageTrackerService,
    private val fileStorageService: FileStorageService,
    private val imageUtilities: ImageUtilities,
    private val compressService: CompressService,
) {
    private val logger = LoggerFactory.getLogger(ResizeController::class.java)

    @PostMapping
    fun resizeImage(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) width: Int?,
        @RequestParam(required = false) height: Int?,
        @RequestParam(required = false) scale: Double?,
        @RequestParam(defaultValue = "true") keepAspectRatio: Boolean,
        @RequestParam(defaultValue = "false") compress: Boolean,
    ): ResponseEntity<ImageResponse> {
        logger.debug("\n\n== RESIZE == ")
        logger.debug("Incoming POST request on /api/resize")
        logger.debug(
            "width: $width, height: $height, scale: $scale, keepAspectRatio: $keepAspectRatio, compress: $compress"
        )
        var tempFile: File? = null // Declare tempFile outside of try block to reference in finally

        try {

            // Validate input parameters
            if (scale == null && width == null && height == null) {
                logger.error("You must specify scale, width, or height.")
                return ResponseEntity.badRequest()
                    .body(
                        ImageResponse(
                            isError = true,
                            error = "You must specify scale, width, or height.",
                        )
                    )
            }

            // Get the image from the request
            val imageRequestData = imageService.getImageInfoFromRequest(file)
            logger.debug("imageRequestData - originalName: ${imageRequestData.originalName}")
            logger.debug("imageRequestData - originalFormat: ${imageRequestData.originalFormat}")
            logger.debug(
                "imageRequestData - originalFileSize: ${imageRequestData.originalFileSize}"
            )

            // Convert MultipartFile to a temporary File
            tempFile = File.createTempFile("upload-", ".${imageRequestData.originalFormat}")
            file.transferTo(tempFile) // Save the uploaded image as a file

            // Perform resizing
            var processedImageBytes =
                resizeService.resizeImage(
                    imageFile = tempFile, // Use the temporary file
                    originalFileName = imageRequestData.originalName,
                    format = imageRequestData.originalFormat,
                    width = width,
                    height = height,
                    scale = scale,
                    keepAspectRatio = keepAspectRatio,
                )

            // If compress parameter is true, compress the resized image
            if (compress) {
                logger.debug("Compressing resized image")
                logger.debug("Original resized size", processedImageBytes.size.toLong())

                // Create ImageRequestData for the resized image
                val resizedImageRequestData =
                    ImageRequestData(
                        originalName = imageRequestData.originalName,
                        originalFormat = imageRequestData.originalFormat,
                        originalFileSize = processedImageBytes.size.toLong(),
                        imageFile = null, // Set to null if not available
                        rawBytes = processedImageBytes,
                    )

                // Compress the resized image
                val compressionResult =
                    compressService.compressImage(resizedImageRequestData, CompressionType.LOSSLESS)

                // Update processedImageBytes with compressed data
                processedImageBytes = compressionResult.compressedData
                logger.debug("Compressed resized size", processedImageBytes.size.toLong())
            }

            // Store the processed image (resized and possibly compressed)
            val uniqueFileName =
                fileStorageService.storeImageAndScheduleDeletion(
                    processedImageBytes,
                    imageRequestData.originalName,
                    imageRequestData.originalFormat,
                )

            // Generate the download URL
            val downloadUrl = fileStorageService.createDownloadLink(uniqueFileName)

            // Log the count for the usage tracking
            usageTrackerService.incrementServiceCount(Services.RESIZE)

            val compressPercent =
                getCompressionPercent(
                    imageRequestData.originalFileSize,
                    processedImageBytes.size.toLong(),
                )

            // Prepare the response
            val responseBody =
                ImageResponse(
                    url = downloadUrl,
                    originalFilename = imageRequestData.originalName,
                    originalFileSize = imageRequestData.originalFileSize.toString(),
                    originalFormat = imageRequestData.originalFormat,
                    compressedSize = processedImageBytes.size.toString(),
                    compressionPercentage = compressPercent.toString(),
                )

            val headers =
                createCustomHeaders(
                    originalFilename = imageRequestData.originalName,
                    originalFileSize = imageRequestData.originalFileSize.toString(),
                    originalFormat = imageRequestData.originalFormat,
                    compressedSize = processedImageBytes.size.toString(),
                    compressionPercentage = compressPercent.toString(),
                    uniqueFilename = uniqueFileName,
                    customContentType =
                        imageUtilities.determineMediaType(
                            processedImageBytes,
                            imageRequestData.originalName,
                        ),
                    contentType = MediaType.APPLICATION_JSON,
                    inline = false,
                )

            return ResponseEntity.ok().headers(headers).body(responseBody)
        } catch (e: Exception) {
            // Handle exceptions
            logger.error("Error resizing image: ${e.message}", e)
            return ResponseEntity.status(500)
                .body(ImageResponse(isError = true, error = "Error resizing image: ${e.message}"))
        } finally {
            tempFile?.delete()
        }
    }
}
