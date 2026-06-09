package dev.slidev.intellij.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlidevFrontmatterBlocksTest {

    private val deck = listOf(
        /* 0 */ "---",
        /* 1 */ "title: Demo",
        /* 2 */ "theme: default",
        /* 3 */ "---",
        /* 4 */ "",
        /* 5 */ "# First",
        /* 6 */ "",
        /* 7 */ "---",
        /* 8 */ "layout: two-cols",
        /* 9 */ "---",
        /* 10 */ "",
        /* 11 */ "second",
        /* 12 */ "",
        /* 13 */ "```md",
        /* 14 */ "---",
        /* 15 */ "not: frontmatter",
        /* 16 */ "---",
        /* 17 */ "```",
    ).joinToString("\n")

    private fun blocks(text: String = deck) = SlidevFrontmatterBlocks.blocks(text, "/slides.md")

    @Test
    fun `finds headmatter and slide frontmatter but not fenced separators`() {
        val blocks = blocks()
        assertEquals(2, blocks.size)
        assertEquals(0, blocks[0].slideIndex)
        assertEquals(1..2, blocks[0].contentLines)
        assertEquals(1, blocks[1].slideIndex)
        assertEquals(8..8, blocks[1].contentLines)
    }

    @Test
    fun `blockAt hits content lines only`() {
        val blocks = blocks()
        assertEquals(blocks[0], SlidevFrontmatterBlocks.blockAt(blocks, 1))
        assertEquals(blocks[0], SlidevFrontmatterBlocks.blockAt(blocks, 2))
        assertNull(SlidevFrontmatterBlocks.blockAt(blocks, 0)) // opening ---
        assertNull(SlidevFrontmatterBlocks.blockAt(blocks, 3)) // closing ---
        assertNull(SlidevFrontmatterBlocks.blockAt(blocks, 5)) // content
        assertNull(SlidevFrontmatterBlocks.blockAt(blocks, 15)) // inside code fence
    }

    @Test
    fun `unclosed frontmatter yields no block`() {
        // Without a closing `---` the parser assigns no frontmatter style at all.
        assertEquals(0, blocks("# A\n\n---\nlayout: cover\nclicks: 2").size)
    }

    @Test
    fun `crlf input keeps line indices`() {
        val blocks = blocks(deck.replace("\n", "\r\n"))
        assertEquals(2, blocks.size)
        assertEquals(1..2, blocks[0].contentLines)
        assertEquals(8..8, blocks[1].contentLines)
    }

    @Test
    fun `key context with typed prefix`() {
        val lines = deck.lines()
        val context = SlidevFrontmatterBlocks.contextAt(lines, blocks(), 8, 3)
        val key = context as SlidevFrontmatterBlocks.Context.Key
        assertEquals("lay", key.prefix)
        assertEquals(1, key.block.slideIndex)
    }

    @Test
    fun `value context carries key and value prefix`() {
        val lines = deck.lines()
        val context = SlidevFrontmatterBlocks.contextAt(lines, blocks(), 8, 10)
        val value = context as SlidevFrontmatterBlocks.Context.Value
        assertEquals("layout", value.key)
        assertEquals("tw", value.prefix)
    }

    @Test
    fun `no context on content or nested lines`() {
        val lines = deck.lines()
        assertNull(SlidevFrontmatterBlocks.contextAt(lines, blocks(), 5, 0))
        val nested = listOf("---", "fonts:", "  sans: Roboto", "---").joinToString("\n")
        val nestedBlocks = blocks(nested)
        assertNull(SlidevFrontmatterBlocks.contextAt(nested.lines(), nestedBlocks, 2, 7))
    }

    @Test
    fun `src value with columns`() {
        val text = listOf("# A", "", "---", "src: ./pages/imported.md", "---").joinToString("\n")
        val src = SlidevFrontmatterBlocks.srcValueAt(text.lines(), blocks(text), 3)!!
        assertEquals("./pages/imported.md", src.value)
        assertEquals(5 until 24, src.columns)
    }

    @Test
    fun `src value keeps range suffix and strips quotes and comments`() {
        fun srcAt(line: String): SlidevFrontmatterBlocks.SrcValue? {
            val text = listOf("# A", "", "---", line, "---").joinToString("\n")
            return SlidevFrontmatterBlocks.srcValueAt(text.lines(), blocks(text), 3)
        }
        assertEquals("./a.md#2,5-7", srcAt("src: ./a.md#2,5-7")?.value)
        assertEquals("./a.md", srcAt("src: './a.md'")?.value)
        assertEquals(5 until 13, srcAt("src: \"./a.md\"")?.columns) // quotes stay clickable
        assertEquals("./a.md", srcAt("src: ./a.md # reused")?.value)
        assertEquals(5 until 11, srcAt("src: ./a.md # reused")?.columns)
        assertNull(srcAt("src:"))
        assertNull(srcAt("layout: cover"))
    }

    @Test
    fun `no src value outside frontmatter blocks`() {
        val text = listOf("src: ./a.md", "", "---", "layout: cover", "---").joinToString("\n")
        assertNull(SlidevFrontmatterBlocks.srcValueAt(text.lines(), blocks(text), 0))
    }

    @Test
    fun `present keys and key line lookup`() {
        val lines = deck.lines()
        val first = blocks().first()
        assertEquals(setOf("title", "theme"), SlidevFrontmatterBlocks.presentKeys(lines, first))
        assertEquals(2, SlidevFrontmatterBlocks.keyLine(lines, first, "theme"))
        assertNull(SlidevFrontmatterBlocks.keyLine(lines, first, "layout"))
    }
}
