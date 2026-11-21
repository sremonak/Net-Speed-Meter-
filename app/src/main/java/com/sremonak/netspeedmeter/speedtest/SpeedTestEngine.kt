package com.sremonak.netspeedmeter.speedtest

import android.os.SystemClock
import com.sremonak.netspeedmeter.info.PublicIpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.random.Random

enum class TestPhase(val label: String) {
    IDLE("Ready"),
    CONNECTING("Connecting"),
    PING("Ping"),
    DOWNLOAD("Download"),
    UPLOAD("Upload"),
    DONE("Complete"),
    ERROR("Error"),
}

data class SpeedTestState(
    val phase: TestPhase = TestPhase.IDLE,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val liveMbps: Double = 0.0,
    val progress: Float = 0f,
    val server: String? = null,
    val clientIp: String? = null,
    val isp: String? = null,
    val dataUsedBytes: Long = 0,
    val error: String? = null,
    val points: List<Float> = emptyList(),
) {
    val running: Boolean get() = phase in setOf(TestPhase.CONNECTING, TestPhase.PING, TestPhase.DOWNLOAD, TestPhase.UPLOAD)
}

/** Multi-connection HTTP speed test against Cloudflare's global edge (speed.cloudflare.com). */
class SpeedTestEngine(
    private val downloadMs: Long = 10_000,
    private val uploadMs: Long = 8_000,
) {
    private val base = "https://speed.cloudflare.com"

    fun run(): Flow<SpeedTestState> = channelFlow {
        var st = SpeedTestState(phase = TestPhase.CONNECTING)
        send(st)
        try {
            val meta = runCatching { PublicIpFetcher.cloudflare() }.getOrNull()
            st = st.copy(
                server = meta?.let { listOfNotNull("Cloudflare", it.edge, it.city).joinToString(" · ") } ?: "Cloudflare",
                clientIp = meta?.ip,
                isp = meta?.isp,
            )

            // ---- Ping / jitter ----
            st = st.copy(phase = TestPhase.PING, progress = 0f)
            send(st)
            val samples = ArrayList<Double>()
            val rounds = 12
            for (i in 0 until rounds) {
                val t = latencyOnce()
                if (i > 0 && t != null) samples += t // first request includes TLS handshake
                st = st.copy(progress = (i + 1f) / rounds, pingMs = samples.medianOrNull())
                send(st)
            }
            if (samples.isEmpty()) throw IOException("Test server unreachable. Check your connection.")
            val jitter = if (samples.size > 1) samples.zipWithNext { a, b -> abs(a - b) }.average() else 0.0
            st = st.copy(pingMs = samples.medianOrNull(), jitterMs = jitter)

            // ---- Download ----
            st = st.copy(phase = TestPhase.DOWNLOAD, progress = 0f, liveMbps = 0.0, points = emptyList())
            send(st)
            val usedBefore = st.dataUsedBytes
            val down = throughput(upload = false, durationMs = downloadMs, workers = 4) { live, progress, bytes ->
                st = st.copy(liveMbps = live, progress = progress, points = st.points + live.toFloat(), dataUsedBytes = usedBefore + bytes)
                send(st)
            }
            st = st.copy(downloadMbps = down, liveMbps = 0.0)

            // ---- Upload ----
            st = st.copy(phase = TestPhase.UPLOAD, progress = 0f, points = emptyList())
            send(st)
            val usedBeforeUp = st.dataUsedBytes
            val up = throughput(upload = true, durationMs = uploadMs, workers = 3) { live, progress, bytes ->
                st = st.copy(liveMbps = live, progress = progress, points = st.points + live.toFloat(), dataUsedBytes = usedBeforeUp + bytes)
                send(st)
            }
            st = st.copy(uploadMbps = up, liveMbps = 0.0, phase = TestPhase.DONE, progress = 1f)
            send(st)
        } catch (e: IOException) {
            send(st.copy(phase = TestPhase.ERROR, error = e.message ?: "Network error"))
        }
    }.flowOn(Dispatchers.IO)

    private fun latencyOnce(): Double? = try {
        val start = System.nanoTime()
        val c = open("$base/__down?bytes=0")
        try {
            c.inputStream.use { it.read() }
        } finally {
            c.disconnect()
        }
        (System.nanoTime() - start) / 1_000_000.0
    } catch (e: IOException) {
        null
    }

    private suspend fun throughput(
        upload: Boolean,
        durationMs: Long,
        workers: Int,
        onTick: suspend (liveMbps: Double, progress: Float, bytes: Long) -> Unit,
    ): Double = coroutineScope {
        val counter = AtomicLong(0)
        val connections: MutableSet<HttpURLConnection> = Collections.synchronizedSet(HashSet())
        val start = SystemClock.elapsedRealtime()
        val deadline = start + durationMs
        val payload = if (upload) Random.nextBytes(64 * 1024) else ByteArray(0)

        val jobs = List(workers) {
            launch(Dispatchers.IO) {
                while (isActive && SystemClock.elapsedRealtime() < deadline) {
                    try {
                        if (upload) uploadOnce(counter, deadline, payload, connections)
                        else downloadOnce(counter, deadline, connections)
                    } catch (e: IOException) {
                        if (SystemClock.elapsedRealtime() >= deadline) break
                        delay(150)
                    }
                }
            }
        }

        val window = ArrayDeque<Pair<Long, Long>>()
        var warmT = -1L
        var warmBytes = 0L
        var anyError = true
        try {
            while (SystemClock.elapsedRealtime() < deadline) {
                delay(200)
                val t = SystemClock.elapsedRealtime()
                val b = counter.get()
                if (b > 0) anyError = false
                window.addLast(t to b)
                while (window.size > 10) window.removeFirst()
                val (t0, b0) = window.first()
                val live = if (t > t0) (b - b0) * 8.0 / ((t - t0) / 1000.0) / 1_000_000.0 else 0.0
                if (warmT < 0 && t - start >= WARMUP_MS) {
                    warmT = t
                    warmBytes = b
                }
                onTick(live, ((t - start).toFloat() / durationMs).coerceIn(0f, 1f), b)
            }
        } finally {
            // Unblock any worker stuck in a read/write.
            synchronized(connections) { connections.forEach { runCatching { it.disconnect() } } }
            jobs.forEach { it.cancel() }
        }
        if (anyError) throw IOException(if (upload) "Upload test failed" else "Download test failed")
        val end = SystemClock.elapsedRealtime()
        val total = counter.get()
        val (fromT, fromB) = if (warmT > 0 && end - warmT > 1000) warmT to warmBytes else start to 0L
        (total - fromB) * 8.0 / ((end - fromT).coerceAtLeast(1) / 1000.0) / 1_000_000.0
    }

    private fun downloadOnce(counter: AtomicLong, deadline: Long, conns: MutableSet<HttpURLConnection>) {
        val c = open("$base/__down?bytes=$DOWNLOAD_CHUNK")
        conns += c
        try {
            c.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    counter.addAndGet(n.toLong())
                    if (SystemClock.elapsedRealtime() >= deadline) break
                }
            }
        } finally {
            conns -= c
            c.disconnect()
        }
    }

    private fun uploadOnce(counter: AtomicLong, deadline: Long, payload: ByteArray, conns: MutableSet<HttpURLConnection>) {
        val c = open("$base/__up").apply {
            doOutput = true
            requestMethod = "POST"
            setFixedLengthStreamingMode(UPLOAD_CHUNK)
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        conns += c
        try {
            c.outputStream.use { out ->
                var sent = 0
                while (sent < UPLOAD_CHUNK) {
                    val n = minOf(payload.size, UPLOAD_CHUNK - sent)
                    out.write(payload, 0, n)
                    sent += n
                    counter.addAndGet(n.toLong())
                    if (SystemClock.elapsedRealtime() >= deadline) return
                }
            }
            c.responseCode
        } finally {
            conns -= c
            c.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            useCaches = false
            setRequestProperty("User-Agent", "NetSpeedMeter/1.0 (Android)")
            setRequestProperty("Cache-Control", "no-cache")
        }

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val s = sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    companion object {
        private const val DOWNLOAD_CHUNK = 25_000_000
        private const val UPLOAD_CHUNK = 4 * 1024 * 1024
        private const val WARMUP_MS = 1500L
    }
}
