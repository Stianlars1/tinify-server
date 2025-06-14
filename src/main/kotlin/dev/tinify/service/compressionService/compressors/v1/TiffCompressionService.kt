package dev.tinify.service.compressionService.compressors.v1

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*

@Service
class TiffCompressionService {

    private val logger: Logger = LoggerFactory.getLogger(TiffCompressionService::class.java)

    fun compressTiffUsingImageMagick(inputFile: File): ByteArray {
        logger.info("Compressing TIFF using ImageMagick")

        val tempOutputFile = File.createTempFile("compressed-${UUID.randomUUID()}", ".tiff")
        logger.info("Temporary output file created: ${tempOutputFile.absolutePath}")

        try {
            // Command for ImageMagick to compress TIFF
            val command =
                listOf(
                    "convert",
                    inputFile.absolutePath,
                    "-strip",
                    "-type",
                    "Palette", // Strip metadata and convert to palette type
                    "-compress",
                    "JPEG",
                    "-quality",
                    "75",
                    tempOutputFile.absolutePath,
                )

            logger.info("ProcessBuilder command: $command")

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD)

            val process = processBuilder.start()
            val exitCode =
                if (process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.exitValue()
                } else {
                    process.destroyForcibly()
                    throw RuntimeException("ImageMagick TIFF process timeout")
                }

            if (exitCode != 0) {
                val errorMsg = process.inputStream.bufferedReader().readText()
                logger.error("ImageMagick failed: $errorMsg")
                throw RuntimeException("ImageMagick failed with exit code $exitCode")
            }

            val compressedBytes = Files.readAllBytes(tempOutputFile.toPath())
            logger.info("Compressed TIFF file size: ${compressedBytes.size} bytes")

            return compressedBytes
        } catch (e: IOException) {
            logger.error("Error during TIFF compression", e)
            throw RuntimeException("Error during TIFF compression: ${e.message}", e)
        } finally {
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
                logger.info("Temporary output file deleted")
            }
        }
    }
}
