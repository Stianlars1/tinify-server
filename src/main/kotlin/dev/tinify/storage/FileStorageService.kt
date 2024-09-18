package dev.tinify.storage

import dev.tinify.DOMAIN_FULL
import dev.tinify.getDownloadsDirectory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.imageio.ImageReader

@Service
class FileStorageService(
    private val logger: Logger = LoggerFactory.getLogger(FileStorageService::class.java),
    private val tempDir: String = getDownloadsDirectory(),
    private val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val domainUrl: String = DOMAIN_FULL

) {

    fun storeImageAndScheduleDeletion(
        imageBytes: ByteArray,
        originalFileName: String,
        format: String,
    ): String {
        // Step 1: Generate a unique file name while keeping the original file name intact
        val uniqueFileName = generateUniqueFileName(originalFileName)

        // Step 2: Store the image in a temp folder with the unique file name
        val tempImagePath = saveTempImage(imageBytes, uniqueFileName, format)

        // Step 3: Schedule deletion of the file after 1 hour
        scheduleFileDeletion(tempImagePath)

        // Return the unique filename (or the path, depending on your needs)
        return uniqueFileName
    }

    fun generateUniqueFileName(originalFileName: String): String {
        val uuid = UUID.randomUUID().toString()
        val extension = originalFileName.substringAfterLast('.', "")
        val baseName = originalFileName.substringBeforeLast('.')
        return if (extension.isNotEmpty()) {
            "$baseName.$uuid.$extension"
        } else {
            "$baseName.$uuid"
        }
    }

    fun extractOriginalFilename(uniqueFileName: String): String {
        // Split the unique filename into base name and extension
        val extension = uniqueFileName.substringAfterLast('.', "")
        val baseNameWithUUID = uniqueFileName.substringBeforeLast('.')

        // Split the base name by the last occurrence of '.'
        val lastDotIndex = baseNameWithUUID.lastIndexOf('.')
        return if (lastDotIndex != -1) {
            // There is a UUID in the base name; extract the original base name
            val originalBaseName = baseNameWithUUID.substring(0, lastDotIndex)
            if (extension.isNotEmpty()) {
                "$originalBaseName.$extension"
            } else {
                originalBaseName
            }
        } else {
            // No UUID found; return the unique filename as is
            uniqueFileName
        }
    }

    fun sanitizeFilename(filename: String): String {
        return Paths.get(filename).fileName.toString()
    }


    fun saveTempImage(imageBytes: ByteArray, uniqueFileName: String, format: String): String {
        val tempImagePath = Paths.get(tempDir, uniqueFileName).toString()
        try {
            FileOutputStream(tempImagePath).use { fos ->
                fos.write(imageBytes)
            }
            logger.info("Image saved at: $tempImagePath")
            val originalFileName = extractOriginalFilename(uniqueFileName)
            logger.info("Original file name: $originalFileName")
        } catch (e: IOException) {
            logger.error("Failed to save image at: $tempImagePath", e)
            throw e
        }
        return tempImagePath
    }


    fun scheduleFileDeletion(filePath: String) {
        executorService.schedule({
            val file = File(filePath)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    logger.info("Temporary file deleted: $filePath")
                } else {
                    logger.error("Failed to delete temporary file: $filePath")
                }
            }
        }, 24, TimeUnit.HOURS)
    }

    fun loadImage(uniqueFileName: String): Pair<ByteArray?, String?> {
        val logger = LoggerFactory.getLogger(FileStorageService::class.java)
        val filePath = Paths.get(tempDir, uniqueFileName).toString()
        val file = File(filePath)

        logger.info("Attempting to load image from file: $filePath")

        return if (file.exists()) {
            try {
                // Step 1: Read the file's bytes
                val fileBytes = Files.readAllBytes(file.toPath())
                logger.info("Successfully loaded image: $filePath")

                // Step 2: Restore the original filename (remove unique ID)
                val originalFileName = extractOriginalFilename(uniqueFileName)
                logger.info("Original file name restored: $originalFileName")

                // Step 3: Return both the file's bytes and the original filename
                Pair(fileBytes, originalFileName)
            } catch (e: Exception) {
                logger.error("Error occurred while loading image from: $filePath", e)
                Pair(null, null)
            }
        } else {
            logger.warn("File not found: $filePath")
            Pair(null, null)
        }
    }

    // Get the full path of the image in the temp directory
    fun getImagePath(uniqueFileName: String): String {
        return Paths.get(tempDir, uniqueFileName).toString()
    }


    fun createDownloadLink(uniqueFileNameWithExtension: String): String {
        return "$domainUrl/api/download/$uniqueFileNameWithExtension"
    }

}