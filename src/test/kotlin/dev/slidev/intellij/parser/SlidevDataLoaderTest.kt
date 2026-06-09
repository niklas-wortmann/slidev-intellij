package dev.slidev.intellij.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidevDataLoaderTest {

    private fun providerOf(vararg files: Pair<String, String>) = FileTextProvider { path ->
        files.toMap()[path]
    }

    @Test
    fun `flat deck without imports`() {
        val data = SlidevDataLoader.load(
            "/proj",
            "/proj/slides.md",
            providerOf("/proj/slides.md" to "# One\n\n---\n\n# Two"),
        )
        assertEquals(2, data.slides.size)
        assertEquals(1, data.slides[0].no)
        assertEquals("One", data.slides[0].title)
        assertEquals("Two", data.slides[1].title)
    }

    @Test
    fun `hidden slides are excluded from the resolved deck`() {
        val data = SlidevDataLoader.load(
            "/proj",
            "/proj/slides.md",
            providerOf("/proj/slides.md" to "# One\n---\nhide: true\n---\n\n# Hidden\n\n---\n\n# Three"),
        )
        assertEquals(listOf("One", "Three"), data.slides.map { it.title })
    }

    @Test
    fun `resolveSrcPath mirrors the loader's import resolution`() {
        assertEquals("/proj/pages/a.md", SlidevDataLoader.resolveSrcPath("./pages/a.md", "/proj/slides.md", "/proj"))
        assertEquals("/proj/shared/a.md", SlidevDataLoader.resolveSrcPath("../shared/a.md", "/proj/pages/b.md", "/proj"))
        assertEquals("/proj/pages/a.md", SlidevDataLoader.resolveSrcPath("/pages/a.md", "/elsewhere/c.md", "/proj"))
        assertEquals("/proj/a.md", SlidevDataLoader.resolveSrcPath("./a.md#2,5-7", "/proj/slides.md", "/proj"))
    }

    @Test
    fun `src import inlines slides from another file`() {
        val data = SlidevDataLoader.load(
            "/proj",
            "/proj/slides.md",
            providerOf(
                "/proj/slides.md" to "# One\n---\nsrc: ./other.md\n---\n\n---\n\n# Last",
                "/proj/other.md" to "# Imported A\n\n---\n\n# Imported B",
            ),
        )
        assertEquals(listOf("One", "Imported A", "Imported B", "Last"), data.slides.map { it.title })
        val imported = data.slides[1]
        assertEquals("/proj/other.md", imported.source.filepath)
        assertEquals(1, imported.importChain?.size)
        assertEquals("/proj/slides.md", imported.importChain?.first()?.filepath)
    }

    @Test
    fun `src import with range selects specific slides`() {
        val data = SlidevDataLoader.load(
            "/proj",
            "/proj/slides.md",
            providerOf(
                "/proj/slides.md" to "---\nsrc: ./other.md#2\n---\n",
                "/proj/other.md" to "# A\n\n---\n\n# B\n\n---\n\n# C",
            ),
        )
        assertEquals(listOf("B"), data.slides.map { it.title })
    }

    @Test
    fun `root-relative src resolves against userRoot`() {
        val data = SlidevDataLoader.load(
            "/proj",
            "/proj/pages/entry.md",
            providerOf(
                "/proj/pages/entry.md" to "---\nsrc: /shared/intro.md\n---\n",
                "/proj/shared/intro.md" to "# Shared Intro",
            ),
        )
        assertEquals(listOf("Shared Intro"), data.slides.map { it.title })
    }

    @Test
    fun `frontmatter of the importing slide overrides the imported slide`() {
        val data = SlidevDataLoader.load(
            "/proj",
            "/proj/slides.md",
            providerOf(
                "/proj/slides.md" to "---\nsrc: ./other.md\nlayout: center\n---\n",
                "/proj/other.md" to "---\nlayout: cover\nbackground: blue\n---\n\n# Imported",
            ),
        )
        assertEquals(1, data.slides.size)
        // importing slide's frontmatter wins; `src` itself is stripped
        assertEquals("center", data.slides[0].frontmatter["layout"])
        assertEquals("blue", data.slides[0].frontmatter["background"])
        assertEquals(null, data.slides[0].frontmatter["src"])
    }

    @Test
    fun `missing import is reported as an error`() {
        val data = SlidevDataLoader.load(
            "/proj",
            "/proj/slides.md",
            providerOf("/proj/slides.md" to "---\nsrc: ./missing.md\n---\n"),
        )
        assertEquals(0, data.slides.size)
        val errors = data.errors["/proj/slides.md"].orEmpty()
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("not found"))
    }

    @Test
    fun `nested imports are resolved recursively`() {
        val data = SlidevDataLoader.load(
            "/proj",
            "/proj/slides.md",
            providerOf(
                "/proj/slides.md" to "---\nsrc: ./a.md\n---\n",
                "/proj/a.md" to "# A1\n---\nsrc: ./b.md\n---\n",
                "/proj/b.md" to "# B1",
            ),
        )
        assertEquals(listOf("A1", "B1"), data.slides.map { it.title })
        assertEquals(2, data.slides[1].importChain?.size)
    }

    @Test
    fun `circular imports are detected instead of looping forever`() {
        val data = SlidevDataLoader.load(
            "/proj",
            "/proj/slides.md",
            providerOf(
                "/proj/slides.md" to "---\nsrc: ./a.md\n---\n",
                "/proj/a.md" to "---\nsrc: ./b.md\n---\n",
                "/proj/b.md" to "---\nsrc: ./a.md\n---\n",
            ),
        )
        assertTrue(data.errors.values.flatten().any { it.message.contains("Circular") })
    }

    @Test
    fun `headmatter comes from the first source slide and falls back to first title`() {
        val data = SlidevDataLoader.load(
            "/proj",
            "/proj/slides.md",
            providerOf("/proj/slides.md" to "---\ntheme: default\n---\n\n# My Deck"),
        )
        assertEquals("default", data.headmatter["theme"])
        assertEquals("My Deck", data.headmatter["title"])
    }

    @Test
    fun `markdownFiles map contains every parsed file`() {
        val data = SlidevDataLoader.load(
            "/proj",
            "/proj/slides.md",
            providerOf(
                "/proj/slides.md" to "---\nsrc: ./a.md\n---\n",
                "/proj/a.md" to "# A",
            ),
        )
        assertEquals(setOf("/proj/slides.md", "/proj/a.md"), data.markdownFiles.keys)
    }
}
