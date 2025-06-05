package dev.tinify.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.tinify.CompressionType
import dev.tinify.Services
import dev.tinify.responses.ImageResponse
import dev.tinify.responses.createCustomHeaders
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
    ): ResponseEntity<out Any?> {
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

            // Serialize the response body to JSON
            val objectMapper = ObjectMapper()
            val jsonResponse = objectMapper.writeValueAsString(responseBody)

            // Calculate the byte size of the JSON string
            val jsonResponseBytes = jsonResponse.toByteArray(Charsets.UTF_8)
            // Create headers for the response
            val headers =
                createCustomHeaders(
                    originalFilename = imageRequestData.originalName,
                    originalFileSize = imageRequestData.originalFileSize.toString(),
                    originalFormat = imageRequestData.originalFormat,
                    compressedSize = compressionResult.compressedSize.toString(),
                    compressionPercentage = compressionResult.compressionPercentage.toString(),
                    uniqueFilename = uniqueFileName,
                    customContentType = MediaType.APPLICATION_JSON, // Ensure correct content type
                    contentType = MediaType.APPLICATION_JSON,
                    contentLength = jsonResponseBytes.size.toLong(), // Set Content-Length manually
                    inline = false,
                )

            // Log headers for debugging
            logger.debug("headers: \n {} \n\n", headers)
            logger.debug("Headers Content-Type: ${headers.contentType}")
            logger.debug(
                "Headers Content-Disposition: ${headers.get(HttpHeaders.CONTENT_DISPOSITION)}"
            )
            logger.debug("Headers X-Original-Filename: ${headers.get("X-Original-Filename")}")

            // Return the manually serialized JSON with the correct content-length
            return ResponseEntity.ok()
                .headers(headers)
                .contentLength(jsonResponseBytes.size.toLong()) // Ensure Content-Length is correct
                .body(jsonResponse) // Send the manually serialized JSON
        } catch (e: Exception) {
            logger.error("Error compressing image: ${e.message}", e)
            return ResponseEntity.status(500)
                .body(
                    ImageResponse(isError = true, error = "Error compressing image: ${e.message}")
                )
        }
    }
}
