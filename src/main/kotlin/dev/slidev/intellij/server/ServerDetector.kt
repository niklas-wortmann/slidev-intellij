package dev.slidev.intellij.server

import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * HTTP probing of (potentially) running Slidev dev servers, the counterpart of
 * `useServerDetector` in the VS Code extension. All functions are blocking and
 * platform-free; call them from `Dispatchers.IO`.
 */
object ServerDetector {

    /** What is listening on a port: a Slidev server, possibly an old one without version meta. */
    data class Detection(val isSlidev: Boolean, val compatMode: Boolean, val entry: String?)

    private val SLIDEV_REGEX = Regex("slidev", RegexOption.IGNORE_CASE)
    private val VERSION_REGEX = Regex("""<meta (?:name|property)="slidev:version" content="([^"]+)"""")
    private val ENTRY_REGEX = Regex("""<meta (?:property|charset)="slidev:entry" content="([^"]+)"""")

    /** Classifies a served HTML page, mirroring the regexes of the VS Code extension. */
    fun analyze(html: String): Detection = Detection(
        isSlidev = SLIDEV_REGEX.containsMatchIn(html),
        compatMode = VERSION_REGEX.find(html) == null,
        entry = ENTRY_REGEX.find(html)?.groupValues?.get(1),
    )

    /**
     * Fetches the root page on [port], trying IPv6 loopback before IPv4 like VS Code does.
     * Returns null when nothing answers HTTP on either address.
     */
    fun probe(port: Int, timeoutMs: Long = 1000): Detection? {
        for (host in listOf("[::1]", "127.0.0.1")) {
            val html = fetch("http://$host:$port", timeoutMs) ?: continue
            return analyze(html)
        }
        return null
    }

    fun isPortFree(port: Int): Boolean = try {
        ServerSocket(port).use { true }
    }
    catch (_: Exception) {
        false
    }

    /**
     * First free port at or above [preferred] that no registered project uses, scanning up
     * to 4000 like the upstream `get-port-please` range; falls back to an ephemeral port.
     */
    fun allocPort(preferred: Int, used: Set<Int>): Int {
        val start = maxOf(preferred, (used.maxOrNull() ?: 0) + 1)
        for (port in start..PORT_RANGE_END) {
            if (port !in used && isPortFree(port)) {
                return port
            }
        }
        return ServerSocket(0).use { it.localPort }
    }

    private fun fetch(url: String, timeoutMs: Long): String? = try {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build()
        val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(timeoutMs)).GET().build()
        client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }
    catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    }
    catch (_: Exception) {
        null
    }

    private const val PORT_RANGE_END = 4000
}
