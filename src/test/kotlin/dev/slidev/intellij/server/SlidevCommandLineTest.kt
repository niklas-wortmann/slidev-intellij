package dev.slidev.intellij.server

import org.junit.Assert.assertEquals
import org.junit.Test

class SlidevCommandLineTest {

    @Test
    fun `substitutes args with quoted file name and port`() {
        val command = SlidevCommandLine.substitute("npm exec -c 'slidev \${args}'", "slides.md", 3030)
        assertEquals("npm exec -c 'slidev \"slides.md\" --port 3030'", command)
    }

    @Test
    fun `quoting keeps file names with spaces intact`() {
        val command = SlidevCommandLine.substitute("slidev \${args}", "my slides.md", 3031)
        assertEquals("slidev \"my slides.md\" --port 3031", command)
    }

    @Test
    fun `substitutes standalone port placeholder`() {
        val command = SlidevCommandLine.substitute("slidev \${args} --remote --port \${port}", "slides.md", 4000)
        assertEquals("slidev \"slides.md\" --port 4000 --remote --port 4000", command)
    }

    @Test
    fun `unix shell command wraps with sh -c`() {
        val parts = SlidevCommandLine.shellCommand("npm exec slidev", isWindows = false)
        assertEquals(3, parts.size)
        assertEquals("-c", parts[1])
        assertEquals("npm exec slidev", parts[2])
    }

    @Test
    fun `windows shell command wraps with cmd c`() {
        assertEquals(
            listOf("cmd.exe", "/c", "npm exec slidev"),
            SlidevCommandLine.shellCommand("npm exec slidev", isWindows = true),
        )
    }
}
