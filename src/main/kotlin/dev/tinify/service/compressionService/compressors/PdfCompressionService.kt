package dev.tinify.service.compressionService.compressors

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID

@Service
class PdfCompressionService {

    private val logger: Logger = LoggerFactory.getLogger(PdfCompressionService::class.java)

    fun compressPdf(inputFile: File): ByteArray {
        logger.info("Compressing PDF using Ghostscript")
        val tempOutputFile = File.createTempFile("compressed-${UUID.randomUUID()}", ".pdf")
        logger.info("Temporary output file created: ${tempOutputFile.absolutePath}")

        try {
            val processBuilder =
                ProcessBuilder(
                    "gs", // Ghostscript command
                    "-sDEVICE=pdfwrite",
                    "-dCompatibilityLevel=1.4",
                    "-dPDFSETTINGS=/screen", // Compression level (/screen is low quality, /ebook is
                                             // medium, /prepress is high quality)
                    "-dNOPAUSE",
                    "-dQUIET",
                    "-dBATCH",
                    "-sOutputFile=${tempOutputFile.absolutePath}",
                    inputFile.absolutePath,
                )
            logger.info("ProcessBuilder command: ${processBuilder.command()}")

            val process = processBuilder.start()
            val exitCode = process.waitFor()

            logger.info("Ghostscript process exited with code $exitCode")
            if (exitCode != 0) {
                val errorMsg = process.errorStream.bufferedReader().readText()
                logger.error("Ghostscript PDF compression failed: $errorMsg")
                throw RuntimeException(
                    "Ghostscript PDF compression failed with exit code $exitCode"
                )
            }

            return Files.readAllBytes(tempOutputFile.toPath())
        } catch (e: IOException) {
            logger.error("Error during PDF compression", e)
            throw RuntimeException("Error during PDF compression", e)
        } finally {
            if (tempOutputFile.exists()) {
                logger.info("Temporary output file exists: ${tempOutputFile.absolutePath}")
                tempOutputFile.delete()
                logger.info("Temporary output file deleted")
            }
        }
    }
}
