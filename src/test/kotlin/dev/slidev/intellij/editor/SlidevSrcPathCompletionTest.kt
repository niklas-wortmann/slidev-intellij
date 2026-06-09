package dev.slidev.intellij.editor

import dev.slidev.intellij.editor.SlidevSrcPathCompletion.Candidate
import dev.slidev.intellij.editor.SlidevSrcPathCompletion.Prefix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidevSrcPathCompletionTest {

    @Test
    fun `parsePrefix keeps plain prefixes and detects root-relative ones`() {
        assertEquals(Prefix("", rootRelative = false), SlidevSrcPathCompletion.parsePrefix(""))
        assertEquals(Prefix("./pa", rootRelative = false), SlidevSrcPathCompletion.parsePrefix("./pa"))
        assertEquals(Prefix("/pa", rootRelative = true), SlidevSrcPathCompletion.parsePrefix("/pa"))
    }

    @Test
    fun `parsePrefix strips one leading yaml quote`() {
        assertEquals(Prefix("./pa", rootRelative = false), SlidevSrcPathCompletion.parsePrefix("\"./pa"))
        assertEquals(Prefix("./pa", rootRelative = false), SlidevSrcPathCompletion.parsePrefix("'./pa"))
        assertEquals(Prefix("/p", rootRelative = true), SlidevSrcPathCompletion.parsePrefix("\"/p"))
    }

    @Test
    fun `parsePrefix bails on page ranges`() {
        assertNull(SlidevSrcPathCompletion.parsePrefix("./a.md#2"))
    }

    @Test
    fun `candidates offers files relative to the importer`() {
        val candidates = SlidevSrcPathCompletion.candidates(
            listOf("/deck/pages/intro.md"),
            "/deck/slides.md",
            "/deck",
            rootRelative = false,
        )
        assertEquals(listOf(Candidate("./pages/intro.md", "pages/intro.md")), candidates)
    }

    @Test
    fun `candidates excludes the importer and files outside the root`() {
        val candidates = SlidevSrcPathCompletion.candidates(
            listOf("/deck/slides.md", "/elsewhere/notes.md", "/deck/pages/intro.md"),
            "/deck/slides.md",
            "/deck",
            rootRelative = false,
        )
        assertEquals(listOf(Candidate("./pages/intro.md", "pages/intro.md")), candidates)
    }

    @Test
    fun `candidates from a subdirectory importer step up with double dots`() {
        val candidates = SlidevSrcPathCompletion.candidates(
            listOf("/deck/intro.md", "/deck/pages/more.md"),
            "/deck/pages/section.md",
            "/deck",
            rootRelative = false,
        )
        assertEquals(
            listOf(Candidate("../intro.md", "intro.md"), Candidate("./more.md", "pages/more.md")),
            candidates,
        )
    }

    @Test
    fun `candidates root-relative flavor anchors at the root`() {
        val candidates = SlidevSrcPathCompletion.candidates(
            listOf("/deck/pages/intro.md"),
            "/deck/pages/section.md",
            "/deck",
            rootRelative = true,
        )
        assertEquals(listOf(Candidate("/pages/intro.md", "pages/intro.md")), candidates)
    }

    @Test
    fun `excludedDirectory prunes node_modules and dot directories`() {
        assertTrue(SlidevSrcPathCompletion.excludedDirectory("node_modules"))
        assertTrue(SlidevSrcPathCompletion.excludedDirectory(".git"))
        assertFalse(SlidevSrcPathCompletion.excludedDirectory("pages"))
    }
}
