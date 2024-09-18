package dev.tinify.service.compressionService.compressors

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*

@Service
class PngCompressionService {

    private val logger: Logger = LoggerFactory.getLogger(PngCompressionService::class.java)

    fun compressPngUsingPngQuant(inputFile: File, quality: String = "60-80"): ByteArray {
        logger.info("Compressing PNG using pngquant")
        val tempOutputFile = File.createTempFile("compressed-${UUID.randomUUID()}", ".png")
        logger.info("Temporary output file created: ${tempOutputFile.absolutePath}")

        try {
            val processBuilder = ProcessBuilder(
                "pngquant",
                "--quality=$quality",
                "--output",
                tempOutputFile.absolutePath,
                "--force",
                inputFile.absolutePath
            )
            logger.info("ProcessBuilder command: ${processBuilder.command()}")

            val process = processBuilder.start()
            val exitCode = process.waitFor()

            logger.info("pngquant process exited with code $exitCode")
            if (exitCode != 0) {
                val errorMsg = process.inputStream.bufferedReader().readText()
                logger.error("pngquant failed: $errorMsg")
                throw RuntimeException("pngquant failed with exit code $exitCode")
            }

            return Files.readAllBytes(tempOutputFile.toPath())
        } catch (e: IOException) {
            logger.error("Error during PNG compression", e)
            throw RuntimeException("Error during PNG compression: ${e.message}", e)
        } finally {
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
                logger.info("Temporary output file deleted")
            }
        }
    }

    fun compressPngUsingOptiPNG(inputFile: File): ByteArray {
        logger.info("Compressing PNG using OptiPNG for lossless compression")
        val tempOutputFile = File.createTempFile("compressed-${UUID.randomUUID()}", ".png")

        try {
            val processBuilder = ProcessBuilder("optipng", "-o7", "-out", tempOutputFile.absolutePath, inputFile.absolutePath)
            logger.info("ProcessBuilder command: ${processBuilder.command()}")

            val process = processBuilder.start()
            val exitCode = process.waitFor()

            logger.info("OptiPNG process exited with code $exitCode")
            if (exitCode != 0) {
                val errorMsg = process.inputStream.bufferedReader().readText()
                logger.error("OptiPNG failed: $errorMsg")
                throw RuntimeException("OptiPNG failed with exit code $exitCode")
            }

            return Files.readAllBytes(tempOutputFile.toPath())
        } catch (e: IOException) {
            logger.error("Error during PNG compression", e)
            throw RuntimeException("Error during PNG compression: ${e.message}", e)
        } finally {
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
                logger.info("Temporary output file deleted")
            }
        }
    }
}
