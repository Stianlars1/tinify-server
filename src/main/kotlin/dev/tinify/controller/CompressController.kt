package dev.tinify.controller

import dev.tinify.CompressionType
import dev.tinify.Services
import dev.tinify.responses.ImageResponse
import dev.tinify.service.CompressService
import dev.tinify.service.ImageService
import dev.tinify.service.UsageTrackerService
import dev.tinify.storage.FileStorageService
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
            val compressionResult = compressService.compressImage(
                imageRequestData, compressionType
            )

            // Store the compressed image
            val uniqueFileName = fileStorageService.storeImageAndScheduleDeletion(
                compressionResult.compressedData, imageRequestData.originalName, imageRequestData.originalFormat
            )

            // Generate the download URL
            val downloadUrl = fileStorageService.createDownloadLink(uniqueFileName)

            // Log usage
            usageTrackerService.incrementServiceCount(Services.COMPRESS)

            // Prepare the response
            val responseBody = ImageResponse(
                url = downloadUrl,
                originalFilename = imageRequestData.originalName,
                originalFileSize = imageRequestData.originalFileSize.toString(),
                originalFormat = imageRequestData.originalFormat,
                compressedSize = compressionResult.compressedSize.toString(),
                compressionPercentage = compressionResult.compressionPercentage.toString()
            )

            return ResponseEntity.ok(responseBody)
        } catch (e: Exception) {
            logger.error("Error compressing image: ${e.message}", e)
            return ResponseEntity.status(500)
                .body(ImageResponse(isError = true, error = "Error compressing image: ${e.message}"))
        }
    }

    @PostMapping("/image")
    fun compressImage(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("compressionType", defaultValue = "LOSSLESS") compressionType: CompressionType,
    ): ResponseEntity<ByteArray> {
        logger.debug("\n\n== POST == ")
        logger.debug("Incoming POST request on /api/compress/image")
        logger.debug("Compression type: {}", compressionType)

        // Get the original format, name, and image file from the request

        // Get the image from the request
        val imageRequestData = imageService.getImageFromRequest(file)
        logger.debug("Original name: {}", imageRequestData.originalName)
        logger.debug("Original format: {}", imageRequestData.originalFormat)
        logger.debug("Original file size: {}", imageRequestData.originalFileSize)


        // Compress the image
        val compressionResult = compressService.compressImage(
            imageRequestData, compressionType
        )
        // Store the compressed image
        val uniqueFileName = fileStorageService.storeImageAndScheduleDeletion(
            compressionResult.compressedData, imageRequestData.originalName, imageRequestData.originalFormat
        )

        // Generate the download URL
        val downloadUrl = fileStorageService.createDownloadLink(uniqueFileName)


        // Prepare the response headers to send back the image in the original format
        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType("image/${imageRequestData.originalFormat}")

        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$imageRequestData.originalName")

        logger.debug("compressionResult - compressedSize: ${compressionResult.compressedSize}")
        logger.debug("compressionResult - compressionPercentage: ${compressionResult.compressionPercentage}")
        logger.debug("uniqueFileName: ${uniqueFileName}")
        // Add compression metadata as custom headers
        headers.add("X-Original-Size", compressionResult.originalSize.toString())
        headers.add("X-Compressed-Size", compressionResult.compressedSize.toString())
        headers.add("X-Compression-Percentage", compressionResult.compressionPercentage.toString())
        headers.add("X-Unique-Filename", uniqueFileName)
        headers.add("X-url", downloadUrl)

        // Log the count for the usage tracking
        usageTrackerService.incrementServiceCount(Services.COMPRESS)

        // Return the compressed image data as the response body
        return ResponseEntity.ok().headers(headers).body((compressionResult.compressedData))
    }

}
