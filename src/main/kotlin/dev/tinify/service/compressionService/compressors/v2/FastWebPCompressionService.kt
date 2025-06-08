package dev.tinify.service.compressionService.compressors.v2

import dev.tinify.CompressionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@Service
class FastWebPCompressionService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun compressWebPInMemory(input: java.io.File, mode: CompressionType): ByteArray {
        val inputBytes = input.readBytes()
        return compressWebPFromBytes(inputBytes, mode)
    }

    fun compressWebPFromBytes(inputBytes: ByteArray, mode: CompressionType): ByteArray {
        val startTime = System.currentTimeMillis()
        val originalSize = inputBytes.size

        return try {
            val result = when (mode) {
                CompressionType.LOSSY -> findBestLossyCompressionFast(inputBytes)
                CompressionType.LOSSLESS -> findBestLosslessCompression(inputBytes)
            }
            val duration = System.currentTimeMillis() - startTime

            if (result.size >= originalSize) {
                log.info("Compressed WebP was not smaller (${result.size} B ≥ ${originalSize} B), returning original")
                inputBytes
            } else {
                log.info("Fast WebP compression: ${originalSize} B → ${result.size} B (${duration} ms)")
                result
            }
        } catch (e: Exception) {
            log.error("Fast WebP compression failed, using original: ${e.message}")
            inputBytes
        }
    }

    private fun findBestLossyCompressionFast(inputBytes: ByteArray): ByteArray {
        val originalSize = inputBytes.size

        // target‐size first
        listOf((0.6 * originalSize).toInt(), (0.5 * originalSize).toInt()).forEach { t ->
            val out = runCwebpWithTempFiles(
                listOf("-quiet", "-size", "$t", "-pass", "10", "-m", "6"),
                inputBytes
            )
            if (out.size < originalSize) return out
        }

        // then quality presets
        listOf(40, 35, 30).forEach { q ->
            val out = runCwebpWithTempFiles(
                listOf(
                    "-quiet",
                    "-q",
                    "$q",
                    "-m",
                    "6",
                    "-pass",
                    "6",
                    "-segments",
                    "4",
                    "-sns",
                    "80",
                    "-f",
                    "25",
                    "-sharpness",
                    "0"
                ),
                inputBytes
            )
            if (out.size < originalSize) return out
        }

        return inputBytes
    }

    private fun findBestLosslessCompression(inputBytes: ByteArray): ByteArray {
        return try {
            runCwebpWithTempFiles(listOf("-quiet", "-lossless"), inputBytes)
        } catch (e: Exception) {
            log.debug("Lossless compression failed: ${e.message}")
            inputBytes
        }
    }

    /**
     * Write inputBytes to a unique temp file, invoke cwebp on it,
     * read back the output file, then clean up.
     */
    private fun runCwebpWithTempFiles(
        args: List<String>,
        inputBytes: ByteArray
    ): ByteArray {
        val inFile = Files.createTempFile("cwebp-in-", ".webp")
        val outFile = Files.createTempFile("cwebp-out-", ".webp")
        try {
            Files.write(inFile, inputBytes)

            val cmd = mutableListOf("cwebp").apply {
                addAll(args)
                add(inFile.toString())
                add("-o")
                add(outFile.toString())
            }
            log.debug("Running: ${cmd.joinToString(" ")}")

            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            if (!proc.waitFor(30, TimeUnit.SECONDS) || proc.exitValue() != 0) {
                val err = proc.inputStream.bufferedReader().readText()
                throw RuntimeException("cwebp failed (exit=${proc.exitValue()}): $err")
            }

            return Files.readAllBytes(outFile)
        } finally {
            try {
                Files.deleteIfExists(inFile)
            } catch (error: IOException) {
                log.error(" Files.deleteIfExists _", error)
            }
            try {
                Files.deleteIfExists(inFile)
            } catch (error: IOException) {
                log.error(" Files.deleteIfExists_", error)
            }
        }
    }
}
