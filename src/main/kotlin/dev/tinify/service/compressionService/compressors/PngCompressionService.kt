package dev.tinify.service.compressionService.compressors

import dev.tinify.CompressionType
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

    // CompressionType enum is defined in the parent package, CompressionType.LOSSY or
    // CompressionType.LOSSLESS
    fun compressPng(inputFile: File, compressionType: CompressionType): ByteArray {
        logger.info("=== compressPng ===")
        logger.info("Compressing PNG file: ${inputFile.absolutePath}")
        if (!inputFile.exists() || !inputFile.canRead()) {
            throw RuntimeException(
                "Input file is not accessible or doesn't exist: ${inputFile.absolutePath}"
            )
        }

        return if (compressionType == CompressionType.LOSSY) {
            compressPngLOSSY(inputFile)
        } else {
            compressPngLOSSLESS(inputFile)
        }
    }

    fun compressPngLOSSY(inputFile: File): ByteArray {
        logger.info("Compressing PNG using LOSSY pipeline")

        val tempPngquantFile = File.createTempFile("pngquant-${UUID.randomUUID()}", ".png")
        val finalOutputFile = File.createTempFile("compressed-${UUID.randomUUID()}", ".png")

        try {
            // Step 1: pngquant lossy compression
            val pngquantProcess =
                ProcessBuilder(
                        "pngquant",
                        "--quality=60-80", // Adjusted to a higher quality range
                        "--speed=1",
                        "--output",
                        tempPngquantFile.absolutePath,
                        "--force",
                        inputFile.absolutePath,
                    )
                    .start()
            val pngquantExitCode = pngquantProcess.waitFor()
            if (pngquantExitCode != 0) {
                val errorMsg = pngquantProcess.errorStream.bufferedReader().readText()
                logger.error("pngquant failed: $errorMsg")
                throw RuntimeException("pngquant failed with exit code $pngquantExitCode")
            }

            val pngquantSize = tempPngquantFile.length()
            var outputFile = tempPngquantFile

            // Step 2: Apply oxipng only if it reduces file size
            val oxipngPath = "/home/bitnami/.cargo/bin/oxipng"
            val oxipngProcess =
                ProcessBuilder(
                        oxipngPath,
                        "--opt",
                        "max",
                        "--strip",
                        "safe",
                        "--out",
                        finalOutputFile.absolutePath,
                        tempPngquantFile.absolutePath,
                    )
                    .start()
            val oxipngExitCode = oxipngProcess.waitFor()

            if (oxipngExitCode == 0) {
                val oxipngSize = finalOutputFile.length()
                if (oxipngSize < pngquantSize) {
                    logger.info("oxipng reduced size from $pngquantSize to $oxipngSize bytes")
                    outputFile = finalOutputFile
                    if (tempPngquantFile.exists()) tempPngquantFile.delete()
                } else {
                    // oxipng didn't reduce size; use pngquant output
                    logger.info("oxipng didn't reduce size; using pngquant output")
                    if (finalOutputFile.exists()) finalOutputFile.delete()
                }
            } else {
                // oxipng failed; use pngquant output
                val errorMsg = oxipngProcess.errorStream.bufferedReader().readText()

                logger.error("oxipng failed: $errorMsg")

                if (finalOutputFile.exists()) finalOutputFile.delete()
            }

            return Files.readAllBytes(outputFile.toPath())
        } catch (e: IOException) {
            logger.error("Error during PNG compression pipeline", e)
            throw RuntimeException("Error during PNG compression pipeline: ${e.message}", e)
        } catch (e: InterruptedException) {
            logger.error("Process was interrupted during PNG compression", e)
            Thread.currentThread().interrupt() // Restore the interrupted status
            throw RuntimeException(
                "Process was interrupted during PNG compression: ${e.message}",
                e,
            )
        } finally {
            // Clean up any remaining temporary files
            if (tempPngquantFile.exists()) tempPngquantFile.delete()
            if (finalOutputFile.exists()) finalOutputFile.delete()
        }
    }

    // lossless using PNGCrush
    fun compressPngLOSSLESS(inputFile: File): ByteArray {
        logger.info("Compressing PNG using PNGCrush (LOSSLESS)")
        val tempOutputFile = File.createTempFile("compressed-${UUID.randomUUID()}", ".png")
        logger.info("Temporary output file created: ${tempOutputFile.absolutePath}")

        try {
            val processBuilder =
                ProcessBuilder(
                    "pngcrush",
                    "-reduce", // Reduce the file size without affecting the image quality
                    "-brute", // Use brute force to find better compression methods
                    inputFile.absolutePath,
                    tempOutputFile.absolutePath, // Output file
                )
            logger.info("ProcessBuilder command: ${processBuilder.command()}")

            val process = processBuilder.start()
            val exitCode = process.waitFor()

            logger.info("pngcrush process exited with code $exitCode")
            if (exitCode != 0) {
                val errorMsg = process.inputStream.bufferedReader().readText()
                logger.error("pngcrush failed: $errorMsg")
                throw RuntimeException("pngcrush failed with exit code $exitCode")
            }

            return Files.readAllBytes(tempOutputFile.toPath())
        } catch (e: IOException) {
            logger.error("Error during PNG compression", e)
            throw RuntimeException("Error during PNG compression: ${e.message}", e)
        } finally {
            // Make sure you delete the tempOutputFile after reading its bytes
            if (tempOutputFile.exists()) {
                logger.info("Temporary output file exists: ${tempOutputFile.absolutePath}")
                tempOutputFile.delete() // Safely delete after compression is successful
                logger.info("Temporary output file deleted")
            }
        }
    }
}
