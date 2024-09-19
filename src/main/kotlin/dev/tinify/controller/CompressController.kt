package dev.tinify.controller

import dev.tinify.CompressionType
import dev.tinify.Services
import dev.tinify.responses.ImageResponse
import dev.tinify.responses.createCustomHeaders
import dev.tinify.service.CompressService
import dev.tinify.service.ImageService
import dev.tinify.service.UsageTrackerService
import dev.tinify.storage.FileStorageService
import dev.tinify.storage.ImageUtilities
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/compress")
@CrossOrigin("*")
internal class CompressController(
    private val compressService: CompressService,
    private val imageService: ImageService,
    private val usageTrackerService: UsageTrackerService,
    private val fileStorageService: FileStorageService,
    private val imageUtilities: ImageUtilities,
) {
    private val logger = LoggerFactory.getLogger(CompressController::class.java)

    @PostMapping
    fun compress(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("compressionType", defaultValue = "LOSSLESS") compressionType: CompressionType,
    ): ResponseEntity<ImageResponse> {
        logger.debug("\n\n== POST == ")
        logger.debug("Incoming POST request on /api/compress")
        logger.debug("Compression type: {}", compressionType)
        try {
            // Get the image from the request
            val imageRequestData = imageService.getImageFromRequest(file)

            // Compress the image
            val compressionResult = compressService.compressImage(imageRequestData, compressionType)

            // Store the compressed image
            val uniqueFileName =
                fileStorageService.storeImageAndScheduleDeletion(
                    compressionResult.compressedData,
                    imageRequestData.originalName,
                    imageRequestData.originalFormat,
                )

            // Generate the download URL
            val downloadUrl = fileStorageService.createDownloadLink(uniqueFileName)

            // Log usage
            usageTrackerService.incrementServiceCount(Services.COMPRESS)

            // Prepare the response
            val responseBody =
                ImageResponse(
                    url = downloadUrl,
                    originalFilename = imageRequestData.originalName,
                    originalFileSize = imageRequestData.originalFileSize.toString(),
                    originalFormat = imageRequestData.originalFormat,
                    compressedSize = compressionResult.compressedSize.toString(),
                    compressionPercentage = compressionResult.compressionPercentage.toString(),
                )

            val headers =
                createCustomHeaders(
                    originalFilename = imageRequestData.originalName,
                    originalFileSize = imageRequestData.originalFileSize.toString(),
                    originalFormat = imageRequestData.originalFormat,
                    compressedSize = compressionResult.compressedSize.toString(),
                    compressionPercentage = compressionResult.compressionPercentage.toString(),
                    uniqueFilename = uniqueFileName,
                    customContentType =
                        imageUtilities.determineMediaType(
                            compressionResult.compressedData,
                            imageRequestData.originalName,
                        ),
                    contentType = MediaType.APPLICATION_JSON,
                    inline = false,
                )

            logger.debug("headers: \n {} \n\n", headers)
            logger.debug("Headers Content-Type: ${headers.contentType}")
            logger.debug(
                "Headers Content-Disposition: ${headers.get(HttpHeaders.CONTENT_DISPOSITION)}"
            )
            logger.debug("Headers X-Original-Filename: ${headers.get("X-Original-Filename")}")

            return ResponseEntity.ok().headers(headers).body(responseBody)
        } catch (e: Exception) {
            logger.error("Error compressing image: ${e.message}", e)
            return ResponseEntity.status(500)
                .body(
                    ImageResponse(isError = true, error = "Error compressing image: ${e.message}")
                )
        }
    }
}
