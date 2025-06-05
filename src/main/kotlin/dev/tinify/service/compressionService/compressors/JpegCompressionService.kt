package dev.tinify.service.compressionService.compressors

import dev.tinify.CompressionType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.nio.file.Files

@Service
class JpegCompressionService {

    private val logger: Logger = LoggerFactory.getLogger(JpegCompressionService::class.java)

    fun compressJpegUsingMozjpeg(inputFile: File, compressionType: CompressionType): ByteArray {

        logger.info("Compressing JPEG using mozjpeg")

        try {
            // Create a temporary output file
            val outputFile = File.createTempFile("compressed_", ".jpg")

            // Build the command based on the compression type
            val CJPEG_PATH = "/opt/mozjpeg/bin/cjpeg"
            val JPEGTRAN_PATH = "/opt/mozjpeg/bin/jpegtran"

            val command =
                when (compressionType) {
                    CompressionType.LOSSY -> {
                        listOf(
                            CJPEG_PATH,
                            "-quality",
                            "70",
                            "-optimize",
                            "-progressive",
                            "-outfile",
                            outputFile.absolutePath,
                            inputFile.absolutePath,
                        )
                    }

                    CompressionType.LOSSLESS -> {
                        listOf(
                            JPEGTRAN_PATH,
                            "-copy",
                            "none",
                            "-optimize",
                            "-progressive",
                            "-outfile",
                            outputFile.absolutePath,
                            inputFile.absolutePath,
                        )
                    }
                }

            logger.info("Executing command: ${command.joinToString(" ")}")

            // Execute the command
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
                    throw RuntimeException("mozjpeg process timeout")
                }

            val processOutput = process.inputStream.bufferedReader().readText()
            if (processOutput.isNotBlank()) {
                logger.info("mozjpeg output: $processOutput")
            }

            logger.info("mozjpeg process exited with code $exitCode")

            if (exitCode != 0) {
                logger.error("mozjpeg failed: $processOutput")
                throw RuntimeException("mozjpeg failed with exit code $exitCode")
            }

            // Read the compressed file
            val compressedBytes = Files.readAllBytes(outputFile.toPath())
            logger.info("Compressed file size: ${compressedBytes.size} bytes")

            // Clean up the temporary file
            outputFile.delete()

            return compressedBytes
        } catch (e: IOException) {
            logger.error("Error during JPEG compression", e)
            throw RuntimeException("Error during JPEG compression: ${e.message}", e)
        }
    }

    fun compressJpegUsingJpegOptim(inputFile: File, compressionType: CompressionType): ByteArray {
        logger.info("Compressing JPEG using jpegoptim")

        try {
            // Command options for jpegoptim based on compression type
            val command =
                when (compressionType) {
                    CompressionType.LOSSY -> {
                        listOf(
                            "jpegoptim",
                            "--max=70",
                            "--strip-all",
                            "--overwrite",
                            inputFile.absolutePath,
                        )
                    }

                    CompressionType.LOSSLESS -> {
                        listOf(
                            "jpegoptim",
                            "--overwrite",
                            "--all-progressive",
                            "--optimise",
                            inputFile.absolutePath,
                        )
                    }
                }

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
                    throw RuntimeException("jpegoptim process timeout")
                }

            logger.info("jpegoptim process exited with code $exitCode")
            if (exitCode != 0) {
                val errorMsg = process.inputStream.bufferedReader().readText()
                logger.error("jpegoptim failed: $errorMsg")
                throw RuntimeException("jpegoptim failed with exit code $exitCode")
            }

            // Read the original input file (which has been optimized in place)
            val compressedBytes = Files.readAllBytes(inputFile.toPath())
            logger.info("Compressed file size: ${compressedBytes.size} bytes")

            return compressedBytes
        } catch (e: IOException) {
            logger.error("Error during JPEG compression", e)
            throw RuntimeException("Error during JPEG compression: ${e.message}", e)
        }
    }
}
