package dev.tinify.service.compressionService.compressors

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files

@Service
class SvgOptimizeService {
    private val logger = LoggerFactory.getLogger(SvgOptimizeService::class.java)

    fun compressSvg(svgBytes: ByteArray): ByteArray {
        // Create temp files for input and output SVGs
        val tempInputFile = File.createTempFile("tempSvg", ".svg")
        val tempOutputFile = File.createTempFile("optimizedSvg", ".svg")

        try {
            // Write the original SVG bytes to the temp input file
            Files.write(tempInputFile.toPath(), svgBytes)

            // Use svgo from PATH for portability across environments
            val command =
                listOf("svgo", tempInputFile.absolutePath, "-o", tempOutputFile.absolutePath)
            val processBuilder = ProcessBuilder(command)

            logger.debug("ProcessBuilder command: {}", command.joinToString(" "))

            val process = processBuilder.start()
            process.waitFor()

            // Check if the process was successful
            if (process.exitValue() != 0) {
                throw RuntimeException(
                    "SVGO compression failed: ${process.errorStream.bufferedReader().readText()}"
                )
            }

            // Read and return the compressed SVG bytes
            return Files.readAllBytes(tempOutputFile.toPath())
        } finally {
            // Clean up temp files
            tempInputFile.delete()
            tempOutputFile.delete()
        }
    }
}
