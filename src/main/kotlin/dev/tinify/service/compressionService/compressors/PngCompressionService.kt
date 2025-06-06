package dev.tinify.service.compressionService.compressors

import dev.tinify.CompressionType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Service
class PngCompressionService {

    companion object {
        /** Max runtime **per external tool** – keeps whole request < 10 s for ≤ 3 MP. */
        private val PROC_TIMEOUT: Duration = Duration.ofSeconds(25)

        /** Thresholds used to decide how much aggression we need. */
        private const val BIG_FILE_BYTES = 400_000L     // > 400 kB ⇒ allow “medium”
        private const val HUGE_FILE_BYTES = 900_000L     // > 900 kB ⇒ allow “ultra”
        private const val TARGET_RATIO = 0.30f        // stop once ≤ 30 % of original

        /** pngquant argument helpers */
        private fun pqArgs(qual: String, speed: Int, extra: List<String> = emptyList()) =
            listOf("--quality", qual, "--speed", "$speed", "--strip") + extra
    }

    private val log = LoggerFactory.getLogger(javaClass)

    /* ────────────────────────── public API ────────────────────────── */

    fun compressPng(input: File, mode: CompressionType): ByteArray =
        when (mode) {
            CompressionType.LOSSY -> lossy(input)
            CompressionType.LOSSLESS -> lossless(input)
        }

    /* ────────────────────────── lossy path ───────────────────────── */

    private fun lossy(src: File): ByteArray {
        data class Candidate(val file: File, val size: Long, val label: String)

        var best: Candidate? = null
        val temps: MutableList<File> = mutableListOf()

        /* ---------- helpers ---------- */

        fun tryPq(label: String, args: List<String>) {
            val tmp = tmp("pq").also { temps += it }
            val cmd = listOf("pngquant") + args +
                    listOf("--output", tmp.absolutePath, "--force", src.absolutePath)
            val exit = run(label, cmd, setOf(0, 98, 99))
            if (exit == 0 && tmp.length() in 1 until (best?.size ?: src.length())) {
                best?.file?.delete()
                best = Candidate(tmp, tmp.length(), label)
            } else tmp.delete()
        }

        fun ratio() = best!!.size / src.length().toFloat()
        fun pctSaved() = ((1 - ratio()) * 100).roundToInt()
        fun finish(): ByteArray {
            val w = best!!
            log.info("PNG lossy: ${src.length()} B → ${w.size} B  (~${pctSaved()} % via ${w.label})")
            val bytes = Files.readAllBytes(w.file.toPath())
            temps.filter { it != w.file }.forEach(File::delete)
            return bytes
        }

        /* ---------- 1) FAST pass (UI-safe, sub-second) ---------- */

        tryPq("pq fast 80-100 s6", pqArgs("80-100", 6))

        /* ---------- 2) MEDIUM pass for big sources or weak gain ---------- */

        val bigEnough = src.length() >= BIG_FILE_BYTES
        val stillLarge = best == null || ratio() > 0.7f
        if (bigEnough && stillLarge) {
            tryPq("pq medium 70-95 s5", pqArgs("70-95", 5))
        }

        /* ---------- 3) DEEP pass if we’re > 50 % of original ---------- */

        if (best == null || ratio() > 0.50f) {
            tryPq(
                "pq deep 55-90 s4",
                pqArgs("55-90", 4, listOf("--nofs"))
            )
        }

        /* ---------- 4) ULTRA pass only for huge files still above target ---------- */

        val reallyHuge = src.length() >= HUGE_FILE_BYTES
        if (reallyHuge && ratio() > TARGET_RATIO) {
            tryPq(
                "pq ultra 35-80 s3",
                pqArgs("35-80", 3, listOf("--nofs", "--posterize", "2"))
            )
        }

        if (best == null) {                       // pngquant failed completely
            log.info("pngquant didn’t reduce – returning original")
            return Files.readAllBytes(src.toPath())
        }

        /* ---------- 5) One quick loss-less optimiser (oxipng -o4) ---------- */

        if (ratio() > TARGET_RATIO && toolExists("oxipng")) {
            val tmp = tmp("oxi").also { temps += it }
            val cmd = listOf(
                "oxipng", "--opt", "4", "--strip", "safe",
                "--out", tmp.absolutePath, best!!.file.absolutePath
            )
            if (run("oxipng -o4", cmd, setOf(0)) == 0 &&
                tmp.length() in 1 until best!!.size
            ) {
                best!!.file.delete()
                best = Candidate(tmp, tmp.length(), "oxipng -o4")
            } else tmp.delete()
        }

        /* ---------- 6) Very short Zopfli squeeze (iterations=8) ---------- */

        if (ratio() > TARGET_RATIO && toolExists("zopflipng")) {
            val tmp = tmp("zpf").also { temps += it }
            val cmd = listOf(
                "zopflipng", "--iterations=8",
                best!!.file.absolutePath, tmp.absolutePath
            )
            if (run("zopflipng-8", cmd, setOf(0)) == 0 &&
                tmp.length() in 1 until best!!.size
            ) {
                best!!.file.delete()
                best = Candidate(tmp, tmp.length(), "zopflipng-8")
            } else tmp.delete()
        }

        return finish()
    }

    /* ───────────────────────── loss-less path ───────────────────────── */

    private fun lossless(src: File): ByteArray {
        var best: File = src
        var bestSize = src.length()
        val temps = mutableListOf<File>()

        fun shrink(label: String, buildCmd: (File) -> List<String>) {
            val tmp = tmp(label.take(3)).also { temps += it }
            val exit = run(label, buildCmd(tmp), setOf(0))
            if (exit == 0 && tmp.length() in 1 until bestSize) {
                if (best != src) best.delete()
                best = tmp; bestSize = tmp.length()
                log.info("$label → $bestSize B")
            } else tmp.delete()
        }

        if (toolExists("oxipng"))
            shrink("oxipng -o4") { out ->
                listOf(
                    "oxipng", "--opt", "4", "--strip", "safe",
                    "--out", out.absolutePath, best.absolutePath
                )
            }

        if (toolExists("optipng"))
            shrink("optipng -o3") { out ->
                Files.copy(best.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
                listOf("optipng", "-o3", "-strip", "all", out.absolutePath)
            }

        if (toolExists("advpng") && best != src)
            run("advpng -z2", listOf("advpng", "-z", "-2", best.absolutePath), setOf(0))

        val bytes = Files.readAllBytes(best.toPath())
        temps.filter { it != best }.forEach(File::delete)
        return bytes
    }

    /* ───────────────────────── utilities ───────────────────────── */

    private fun tmp(prefix: String) =
        File.createTempFile("$prefix-${UUID.randomUUID()}", ".png")

    private fun run(label: String, cmd: List<String>, ok: Set<Int>): Int {
        val t0 = System.nanoTime()
        return try {
            val p = ProcessBuilder(cmd)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!p.waitFor(PROC_TIMEOUT.seconds, TimeUnit.SECONDS)) {
                p.destroyForcibly(); log.error("$label ▸ timeout (${PROC_TIMEOUT.seconds}s)"); -1
            } else {
                val ms = Duration.ofNanos(System.nanoTime() - t0).toMillis()
                val exit = p.exitValue()
                if (exit !in ok) log.warn("$label ▸ exit=$exit ($ms ms)")
                else log.debug("$label ▸ OK ($ms ms)")
                exit
            }
        } catch (e: Exception) {
            log.error("$label ▸ failed – ${e.message}"); -1
        }
    }

    private fun toolExists(name: String): Boolean = try {
        ProcessBuilder("which", name)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .let { it.waitFor(1, TimeUnit.SECONDS) && it.exitValue() == 0 }
    } catch (_: Exception) {
        false
    }
}
