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
                        listOf("jpegoptim", "--overwrite", inputFile.absolutePath)
                    }
                }

            logger.info("ProcessBuilder command: $command")

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            val exitCode = process.waitFor()

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
