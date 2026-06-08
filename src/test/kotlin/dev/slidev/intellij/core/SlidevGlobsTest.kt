package dev.slidev.intellij.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidevGlobsTest {

    private val defaults = SlidevGlobs(listOf("**/slides.md"), "**/node_modules/**")

    @Test
    fun `default include matches slides md at any depth`() {
        assertTrue(defaults.matches("slides.md"))
        assertTrue(defaults.matches("docs/slides.md"))
        assertTrue(defaults.matches("a/b/c/slides.md"))
    }

    @Test
    fun `default include rejects other markdown files`() {
        assertFalse(defaults.matches("README.md"))
        assertFalse(defaults.matches("docs/notes.md"))
    }

    @Test
    fun `node_modules is excluded at any depth`() {
        assertFalse(defaults.matches("node_modules/pkg/slides.md"))
        assertFalse(defaults.matches("examples/node_modules/pkg/slides.md"))
    }

    @Test
    fun `multiple include globs are unioned`() {
        val globs = SlidevGlobs(listOf("**/slides.md", "decks/*.md"), null)
        assertTrue(globs.matches("decks/intro.md"))
        assertTrue(globs.matches("sub/slides.md"))
        assertFalse(globs.matches("intro.md"))
    }

    @Test
    fun `blank globs are ignored`() {
        val globs = SlidevGlobs(listOf("", "  ", "**/slides.md"), "")
        assertTrue(globs.matches("slides.md"))
        assertFalse(globs.matches("other.md"))
    }
}
