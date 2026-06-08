package dev.slidev.intellij.server

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket

class ServerDetectorTest {

    private val slidevHtml = """
        <html><head>
        <meta name="slidev:version" content="52.0.0">
        <meta property="slidev:entry" content="/tmp/demo/slides.md">
        <title>Slidev</title>
        </head><body></body></html>
    """.trimIndent()

    private val compatHtml = "<html><head><title>Slidev</title></head><body>slidev app</body></html>"

    private val foreignHtml = "<html><head><title>Some Other App</title></head><body>hello</body></html>"

    @Test
    fun `analyze detects a current slidev server`() {
        val detection = ServerDetector.analyze(slidevHtml)
        assertTrue(detection.isSlidev)
        assertFalse(detection.compatMode)
        assertEquals("/tmp/demo/slides.md", detection.entry)
    }

    @Test
    fun `analyze flags servers without version meta as compat mode`() {
        val detection = ServerDetector.analyze(compatHtml)
        assertTrue(detection.isSlidev)
        assertTrue(detection.compatMode)
        assertNull(detection.entry)
    }

    @Test
    fun `analyze rejects non-slidev pages`() {
        assertFalse(ServerDetector.analyze(foreignHtml).isSlidev)
    }

    @Test
    fun `probe classifies a live server`() {
        withServer(slidevHtml) { port ->
            val detection = ServerDetector.probe(port)
            assertTrue(detection != null && detection.isSlidev)
            assertFalse(detection!!.compatMode)
        }
    }

    @Test
    fun `probe returns null when nothing listens`() {
        val freePort = ServerSocket(0).use { it.localPort }
        assertNull(ServerDetector.probe(freePort, timeoutMs = 250))
    }

    @Test
    fun `isPortFree reflects an occupied port`() {
        ServerSocket(0).use { socket ->
            assertFalse(ServerDetector.isPortFree(socket.localPort))
        }
        val released = ServerSocket(0).use { it.localPort }
        assertTrue(ServerDetector.isPortFree(released))
    }

    @Test
    fun `allocPort skips used and occupied ports`() {
        ServerSocket(0).use { socket ->
            val occupied = socket.localPort
            val allocated = ServerDetector.allocPort(occupied, setOf(occupied))
            assertTrue(allocated != occupied)
            assertTrue(ServerDetector.isPortFree(allocated))
        }
    }

    private fun withServer(html: String, block: (Int) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = html.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            block(server.address.port)
        }
        finally {
            server.stop(0)
        }
    }
}
