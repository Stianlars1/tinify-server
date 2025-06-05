// PngCompressionService.kt (updated)
package dev.tinify.service.compressionService.compressors

import dev.tinify.CompressionType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.util.*

@Service
class PngCompressionService {

    private val logger: Logger = LoggerFactory.getLogger(PngCompressionService::class.java)

    fun compressPng(inputFile: File, compressionType: CompressionType): ByteArray {
        if (!inputFile.exists() || !inputFile.canRead()) {
            throw RuntimeException("Input file is not accessible: ${inputFile.absolutePath}")
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
            val pngquantProcess = ProcessBuilder(
                "pngquant", "--quality=80-90", "--speed=1", "--strip", "--skip-if-larger",
                "--output", tempPngquantFile.absolutePath, "--force", inputFile.absolutePath
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!pngquantProcess.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                pngquantProcess.destroyForcibly()
                throw RuntimeException("pngquant process timeout")
            }
            if (pngquantProcess.exitValue() != 0) {
                val errorMsg = pngquantProcess.errorStream.bufferedReader().readText()
                logger.error("pngquant failed: $errorMsg")
                throw RuntimeException("pngquant failed (code ${pngquantProcess.exitValue()})")
            }

            // Use pngquant output by default
            val pngquantSize = tempPngquantFile.length()
            var outputFile: File = tempPngquantFile

            // Step 2: oxipng (max optimization, safe strip)
            val oxipngProcess = ProcessBuilder(
                "oxipng", "--opt", "max", "--strip", "safe",
                "--out", finalOutputFile.absolutePath, tempPngquantFile.absolutePath
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!oxipngProcess.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                oxipngProcess.destroyForcibly()
                throw RuntimeException("oxipng process timeout")
            }
            if (oxipngProcess.exitValue() == 0) {
                val oxipngSize = finalOutputFile.length()
                if (oxipngSize < pngquantSize) {
                    logger.info("oxipng reduced size from $pngquantSize to $oxipngSize bytes")
                    outputFile = finalOutputFile
                    if (tempPngquantFile.exists()) tempPngquantFile.delete()
                } else {
                    logger.info("oxipng did not reduce size; using pngquant output")
                    if (finalOutputFile.exists()) finalOutputFile.delete()
                }
            } else {
                val errorMsg = oxipngProcess.errorStream.bufferedReader().readText()
                logger.error("oxipng failed: $errorMsg")
                if (finalOutputFile.exists()) finalOutputFile.delete()
            }

            // Additional step: advpng for maximum lossless compression
            if (outputFile.exists()) {
                logger.info("Running advpng for additional PNG compression")
                val advpngProcess = ProcessBuilder("advpng", "-z", "-4", outputFile.absolutePath)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                if (!advpngProcess.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    advpngProcess.destroyForcibly()
                    logger.warn("advpng process timeout")
                } else if (advpngProcess.exitValue() != 0) {
                    logger.warn("advpng did not run successfully (code ${advpngProcess.exitValue()})")
                }
            }

            return Files.readAllBytes(outputFile.toPath())
        } finally {
            tempPngquantFile.delete()
            finalOutputFile.delete()
        }
    }

    // Lossless path: pngcrush then advpng
    fun compressPngLOSSLESS(inputFile: File): ByteArray {
        logger.info("Compressing PNG using PNGCrush (LOSSLESS)")
        val tempOutputFile = File.createTempFile("compressed-${UUID.randomUUID()}", ".png")
        try {
            val processBuilder = ProcessBuilder(
                "pngcrush", "-reduce", "-q",
                inputFile.absolutePath, tempOutputFile.absolutePath
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
            val process = processBuilder.start()
            if (!process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw RuntimeException("pngcrush process timeout")
            }
            if (process.exitValue() != 0) {
                val errorMsg = process.inputStream.bufferedReader().readText()
                logger.error("pngcrush failed: $errorMsg")
                throw RuntimeException("pngcrush failed (code ${process.exitValue()})")
            }
            // AdvPNG for further compression
            if (tempOutputFile.exists()) {
                logger.info("Running advpng for lossless PNG compression")
                val advpngProcess = ProcessBuilder("advpng", "-z", "-4", tempOutputFile.absolutePath)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                if (!advpngProcess.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    advpngProcess.destroyForcibly()
                    logger.warn("advpng process timeout")
                } else if (advpngProcess.exitValue() != 0) {
                    logger.warn("advpng did not run successfully (code ${advpngProcess.exitValue()})")
                }
            }
            return Files.readAllBytes(tempOutputFile.toPath())
        } finally {
            tempOutputFile.delete()
        }
    }
}
