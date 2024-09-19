package dev.tinify

import dev.tinify.storage.FileStorageService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.system.ApplicationHome
import java.io.File
import java.nio.file.Paths
import javax.imageio.ImageIO

val logger: Logger = LoggerFactory.getLogger(TinifyApplication::class.java)

@SpringBootApplication class TinifyApplication

fun main(args: Array<String>) {
    logger.info("System PATH: ${System.getenv("PATH")}")
    setupTempDir()
    scanAndCheckAvailableImageWriters()
    runApplication<TinifyApplication>(*args)
}

// Check acvailable webp writers
fun scanAndCheckAvailableImageWriters() {
    logger.info("\n\n==== Scanning for plugins ====")
    ImageIO.scanForPlugins()

    logger.info("\n\n==== All Readers ====")
    for (reader in ImageIO.getReaderFormatNames()) {
        println(" - Found writer for format: $reader")
    }

    logger.info("\n\n==== All Writers ====")
    for (writer in ImageIO.getWriterFormatNames()) {
        println(" - Found writer for format: $writer")
    }

    logger.info("\n\n==== Available WebP Writers ====")
    val writers = ImageIO.getImageWritersByFormatName("webp")
    if (writers.hasNext()) {
        logger.info("\n - WebP writer found: ${writers.next()}")
    } else {
        logger.info("No WebP writer found")
    }
}

fun getDownloadsDirectory(): String {
    // Use ApplicationHome to determine the base path dynamically
    val home = ApplicationHome(FileStorageService::class.java)
    val basePath = home.dir.absolutePath
    val tempDir = Paths.get(basePath, DOWNLOADS_FOLDER).toString()

    val dir = File(tempDir)
    if (!dir.exists()) {
        setupTempDir()
    }

    return Paths.get(basePath, DOWNLOADS_FOLDER).toString()
}

fun setupTempDir() {
    lateinit var tempDir: String // Declare tempDir as lateinit var

    // Use ApplicationHome to determine the base path dynamically
    val home = ApplicationHome(TinifyApplication::class.java)
    val basePath = home.dir.absolutePath

    tempDir = Paths.get(basePath, DOWNLOADS_FOLDER).toString()

    logger.info("Current module base path: $basePath") // Log the module base path

    // Create temp folder if it doesn't exist
    val dir = File(tempDir)
    if (!dir.exists()) {
        val created = dir.mkdirs()
        if (created) {
            logger.info("Downloads directory created at: $tempDir")
        } else {
            logger.error("Failed to create Downloads directory at: $tempDir")
        }
    } else {
        logger.info("Temporary Downloads already exists at: $tempDir")
    }
}
