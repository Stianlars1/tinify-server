package dev.tinify.service.compressionService.compressors.v2

import dev.tinify.CompressionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@Service
class FastWebPCompressionService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun compressWebPInMemory(input: File, mode: CompressionType): ByteArray {
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

            // CRITICAL: Always return original if compressed is not smaller
            if (result.size >= originalSize) {
                log.info("Compressed WebP was not smaller (${result.size} B >= ${originalSize} B), returning original")
                return inputBytes
            }

            log.info("Fast WebP compression: ${originalSize} B → ${result.size} B (${duration}ms)")
            result
        } catch (e: Exception) {
            log.error("Fast WebP compression failed, using original: ${e.message}")
            inputBytes
        }
    }

    private fun findBestLossyCompressionFast(inputBytes: ByteArray): ByteArray = runBlocking {
        // Use the EXACT settings that achieved 47-57% compression with temp files
        val compressionAttempts = listOf(
            Triple("working_target", (inputBytes.size * 0.6).toInt(), "Target 40% reduction"),
            Triple("working_target", (inputBytes.size * 0.5).toInt(), "Target 50% reduction"),
            Triple("working_quality", 40, "Quality 40"),
            Triple("working_quality", 35, "Quality 35"),
            Triple("working_quality", 30, "Quality 30"),
        )

        // Run compressions in parallel
        val results = compressionAttempts.map { (type, value, description) ->
            async(Dispatchers.IO) {
                try {
                    val compressed = when (type) {
                        "working_target" -> compressWithWorkingTargetSize(inputBytes, value)
                        "working_quality" -> compressWithWorkingQuality(inputBytes, value)
                        else -> null
                    }

                    if (compressed != null && compressed.size < inputBytes.size) {
                        log.info("SUCCESS: ${description} achieved ${inputBytes.size} → ${compressed.size} bytes (${((inputBytes.size - compressed.size) * 100 / inputBytes.size)}% reduction)")
                        Pair(compressed, description)
                    } else {
                        log.debug("FAILED: ${description} produced ${compressed?.size ?: 0} bytes")
                        null
                    }
                } catch (e: Exception) {
                    log.debug("ERROR: ${description} failed: ${e.message}")
                    null
                }
            }
        }

        // Wait for all results and pick the best one
        val completedResults = results.awaitAll().filterNotNull()

        if (completedResults.isNotEmpty()) {
            val bestResult = completedResults.minByOrNull { it.first.size }
            if (bestResult != null) {
                log.info("Best parallel compression: ${bestResult.first.size} bytes using ${bestResult.second}")
                return@runBlocking bestResult.first
            }
        }

        log.info("All compression attempts failed, returning original")
        inputBytes
    }

    private fun compressWithWorkingTargetSize(inputBytes: ByteArray, targetSize: Int): ByteArray {
        // Use the EXACT parameters that worked with temp files
        val command = listOf(
            "cwebp",
            "-size", targetSize.toString(),
            "-pass", "10",  // Back to the working settings
            "-m", "6",      // Back to the working settings
            "-quiet",
            "-o", "-",     // ← stdout
            "--", "-"               // ✅ FIXED: Use - instead of /dev/stdin
        )

        log.debug("Working target size compression: ${targetSize} bytes")
        return executeWebPCommandWithProperThreading(command, inputBytes)
    }

    private fun compressWithWorkingQuality(inputBytes: ByteArray, quality: Int): ByteArray {
        // Use the EXACT parameters that worked with temp files
        val command = listOf(
            "cwebp",
            "-q", quality.toString(),
            "-m", "6",      // Back to the working settings
            "-pass", "6",   // Back to the working settings
            "-segments", "4",
            "-sns", "80",
            "-f", "25",
            "-sharpness", "0",
            "-quiet",
            "-o", "-",     // ← stdout
            "--", "-"      // ← stdin via -- switch
        )

        log.debug("Working quality compression: q=${quality}")
        return executeWebPCommandWithProperThreading(command, inputBytes)
    }

    private fun findBestLosslessCompression(inputBytes: ByteArray): ByteArray {
        val command = listOf(
            "cwebp",
            "-lossless",
            "-z", "6",
            "-m", "4",
            "-quiet",
            "-o", "-",     // ← stdout
            "--", "-"      // ← stdin via -- switch
        )

        return try {
            executeWebPCommandWithProperThreading(command, inputBytes)
        } catch (e: Exception) {
            log.debug("Lossless compression failed: ${e.message}")
            inputBytes
        }
    }

    /**
     * FIXED: Proper concurrent threading to avoid pipe buffer deadlock
     * FIXED: Using - instead of /dev/stdin and /dev/stdout for proper streaming
     */
    private fun executeWebPCommandWithProperThreading(command: List<String>, inputBytes: ByteArray): ByteArray {
        log.debug("Executing cwebp command: ${command.joinToString(" ")} on ${inputBytes.size} bytes")

        val process = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        // Atomic references for thread communication
        val outputResult = AtomicReference<ByteArray>()
        val stdinException = AtomicReference<Exception>()
        val stdoutException = AtomicReference<Exception>()
        val errorOutput = AtomicReference<String>()

        // Start stderr reader to capture any errors
        val stderrThread = thread(name = "webp-stderr-reader") {
            try {
                val error = process.errorStream.bufferedReader().readText()
                errorOutput.set(error)
                if (error.isNotEmpty()) {
                    log.debug("cwebp stderr: $error")
                }
            } catch (e: Exception) {
                log.debug("Stderr reading failed: ${e.message}")
            }
        }

        // CRITICAL: Start stdout reader IMMEDIATELY before writing to stdin
        val stdoutThread = thread(name = "webp-stdout-reader") {
            try {
                val output = ByteArrayOutputStream()
                process.inputStream.use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
                val result = output.toByteArray()
                outputResult.set(result)
                log.debug("cwebp produced ${result.size} bytes output")
            } catch (e: Exception) {
                stdoutException.set(e)
                log.debug("Stdout reading failed: ${e.message}")
            }
        }

        // CRITICAL: Start stdin writer IMMEDIATELY after stdout reader
        val stdinThread = thread(name = "webp-stdin-writer") {
            try {
                process.outputStream.use { outputStream ->
                    outputStream.write(inputBytes)
                    outputStream.flush()
                }
                log.debug("Successfully wrote ${inputBytes.size} bytes to cwebp stdin")
            } catch (e: Exception) {
                stdinException.set(e)
                log.debug("Stdin writing failed: ${e.message}")
            }
        }

        return try {
            // Wait for threads with reasonable timeouts
            stdinThread.join(5000)
            stdoutThread.join(10000)
            stderrThread.join(2000)

            // Check if threads completed successfully
            if (stdinThread.isAlive) {
                stdinThread.interrupt()
                throw RuntimeException("stdin writing timeout")
            }

            if (stdoutThread.isAlive) {
                stdoutThread.interrupt()
                throw RuntimeException("stdout reading timeout")
            }

            // Wait for process to complete
            val exitCode = process.waitFor()

            log.debug("cwebp completed with exit code $exitCode")

            if (exitCode != 0) {
                val errorMsg = errorOutput.get() ?: "Unknown error"
                log.error("cwebp failed with exit code $exitCode: $errorMsg")
                throw RuntimeException("cwebp failed with exit code $exitCode: $errorMsg")
            }

            // Check for exceptions in threads
            stdinException.get()?.let { throw it }
            stdoutException.get()?.let { throw it }

            val result = outputResult.get() ?: throw RuntimeException("No output received")

            log.debug("cwebp compression result: ${inputBytes.size} → ${result.size} bytes (${if (result.size < inputBytes.size) "success" else "larger"})")

            if (result.isEmpty()) {
                throw RuntimeException("Empty output received")
            }

            result
        } catch (e: Exception) {
            process.destroyForcibly()
            throw e
        } finally {
            // Cleanup any remaining threads
            if (stdinThread.isAlive) stdinThread.interrupt()
            if (stdoutThread.isAlive) stdoutThread.interrupt()
            if (stderrThread.isAlive) stderrThread.interrupt()
        }
    }
}





















