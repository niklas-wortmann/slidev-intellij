package dev.slidev.intellij.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidevParserTest {

    private fun parse(md: String) = SlidevParser.parse(md, "/test/slides.md")

    @Test
    fun `single slide without frontmatter`() {
        val md = parse("# Hello\n\nWorld")
        assertEquals(1, md.slides.size)
        val slide = md.slides[0]
        assertEquals("Hello", slide.title)
        assertEquals(1, slide.level)
        assertEquals("# Hello\n\nWorld", slide.content)
        assertNull(slide.frontmatterStyle)
        assertEquals(0, slide.start)
        assertEquals(0, slide.contentStart)
        assertEquals(3, slide.end)
    }

    @Test
    fun `two slides split by separator`() {
        val md = parse("# One\n\n---\n\n# Two")
        assertEquals(2, md.slides.size)
        assertEquals("One", md.slides[0].title)
        assertEquals("Two", md.slides[1].title)
        // separator line belongs to neither slide
        assertEquals(2, md.slides[0].end)
        assertEquals(3, md.slides[1].start)
    }

    @Test
    fun `headmatter on first slide`() {
        val md = parse("---\ntitle: Deck\ntheme: seriph\n---\n\n# First")
        assertEquals(1, md.slides.size)
        val slide = md.slides[0]
        assertEquals("Deck", slide.frontmatter["title"])
        assertEquals("seriph", slide.frontmatter["theme"])
        assertEquals(FrontmatterStyle.FRONTMATTER, slide.frontmatterStyle)
        assertEquals("Deck", slide.title)
        assertEquals(0, slide.start)
        assertEquals(4, slide.contentStart)
        assertEquals(0..3, slide.frontmatterLines)
    }

    @Test
    fun `per-slide frontmatter`() {
        val md = parse("# One\n---\nlayout: center\n---\n\n# Two")
        assertEquals(2, md.slides.size)
        val second = md.slides[1]
        assertEquals("center", second.frontmatter["layout"])
        assertEquals("Two", second.title)
        // slide with frontmatter includes its opening separator line
        assertEquals(1, second.start)
        assertEquals(4, second.contentStart)
        assertEquals(1..3, second.frontmatterLines)
    }

    @Test
    fun `separator followed by blank line is not frontmatter`() {
        val md = parse("# One\n---\n\nlayout: center\n---\n\n# Two")
        // first ---: next line blank => plain separator. Then "layout..." slide is
        // terminated by the second ---, which is followed by blank => plain separator again.
        assertEquals(3, md.slides.size)
        assertTrue(md.slides[1].content.contains("layout: center"))
        assertEquals(emptyMap<String, Any?>(), md.slides[1].frontmatter)
    }

    @Test
    fun `four dashes is a separator but never frontmatter`() {
        val md = parse("# One\n----\n# Two")
        assertEquals(2, md.slides.size)
        assertEquals("Two", md.slides[1].title)
        assertEquals(emptyMap<String, Any?>(), md.slides[1].frontmatter)
    }

    @Test
    fun `separator inside fenced code block is ignored`() {
        val md = parse("# One\n\n```md\n---\nnot a separator\n---\n```\n\nstill slide one")
        assertEquals(1, md.slides.size)
        assertTrue(md.slides[0].content.contains("not a separator"))
    }

    @Test
    fun `four-backtick fence may contain three-backtick fences`() {
        val md = parse("````md\n```js\nconst a = 1\n```\n---\n````\n\n---\n\n# Two")
        assertEquals(2, md.slides.size)
        assertEquals("Two", md.slides[1].title)
    }

    @Test
    fun `separator inside a magic-move block does not split slides`() {
        // 19.6 regression pin: the fence-skip tracks the leading-backtick run, so the
        // `---` lines between magic-move steps stay inside slide one.
        val md = parse("# One\n\n````md magic-move {at:4} [app.js]\n```js\n---\n```\n---\n```ts\nlet b\n```\n````\n\n---\n\n# Two")
        assertEquals(2, md.slides.size)
        assertEquals("Two", md.slides[1].title)
    }

    @Test
    fun `unclosed code fence does not protect later separators`() {
        // upstream only skips past a fence when it is closed; an unclosed fence
        // leaves the following lines subject to normal separator handling
        val md = parse("# One\n\n```js\n---\n# Not a slide")
        assertEquals(2, md.slides.size)
        assertEquals("Not a slide", md.slides[1].title)
    }

    @Test
    fun `separator inside multi-line html comment is ignored`() {
        val md = parse("# One\n<!--\n---\n-->\n\n---\n\n# Two")
        assertEquals(2, md.slides.size)
        assertEquals("One", md.slides[0].title)
        assertEquals("Two", md.slides[1].title)
    }

    @Test
    fun `trailing html comment becomes the note`() {
        val md = parse("# One\n\nContent\n\n<!-- speaker note -->")
        val slide = md.slides[0]
        assertEquals("speaker note", slide.note)
        assertEquals("# One\n\nContent", slide.content)
    }

    @Test
    fun `html comment in the middle is not a note`() {
        val md = parse("# One\n\n<!-- not a note -->\n\nContent")
        val slide = md.slides[0]
        assertNull(slide.note)
        assertTrue(slide.content.contains("not a note"))
    }

    @Test
    fun `only the last content-final comment becomes the note`() {
        val md = parse("# One\n\n<!-- first -->\n\nContent\n\n<!--\nmulti\nline\n-->")
        val slide = md.slides[0]
        assertEquals("multi\nline", slide.note)
        assertTrue(slide.content.contains("first"))
    }

    @Test
    fun `title from frontmatter overrides heading`() {
        val md = parse("---\ntitle: From Frontmatter\n---\n\n# From Heading")
        assertEquals("From Frontmatter", md.slides[0].title)
    }

    @Test
    fun `name is a fallback for title`() {
        val md = parse("---\nname: My Name\n---\n\ncontent")
        assertEquals("My Name", md.slides[0].title)
    }

    @Test
    fun `level from heading and frontmatter override`() {
        assertEquals(3, parse("### Deep").slides[0].level)
        assertEquals(2, parse("---\nlevel: 2\n---\n\n### Deep").slides[0].level)
    }

    @Test
    fun `yaml code block frontmatter style`() {
        val md = parse("```yaml\nlayout: cover\n```\n\n# Hello")
        val slide = md.slides[0]
        assertEquals(FrontmatterStyle.YAML, slide.frontmatterStyle)
        assertEquals("cover", slide.frontmatter["layout"])
        assertNull(slide.frontmatterLines)
    }

    @Test
    fun `crlf line endings`() {
        val md = parse("# One\r\n\r\n---\r\n\r\n# Two")
        assertEquals(2, md.slides.size)
        assertEquals("One", md.slides[0].title)
        assertEquals("Two", md.slides[1].title)
    }

    @Test
    fun `hidden flags`() {
        val md = parse("---\nhide: true\n---\n\n# Hidden\n---\ndisabled: true\n---\n\n# Disabled\n\n---\n\n# Shown")
        assertEquals(3, md.slides.size)
        assertTrue(md.slides[0].isHidden)
        assertTrue(md.slides[1].isHidden)
        assertTrue(!md.slides[2].isHidden)
    }

    @Test
    fun `invalid yaml frontmatter yields empty map but still strips the block`() {
        val md = parse("---\n{ this is: [ not yaml\n---\n\n# Title")
        val slide = md.slides[0]
        assertEquals(emptyMap<String, Any?>(), slide.frontmatter)
        assertEquals("Title", slide.title)
        assertTrue(!slide.content.contains("not yaml"))
    }

    @Test
    fun `revision is stable and changes with content`() {
        val a1 = parse("# A").slides[0].revision
        val a2 = parse("# A").slides[0].revision
        val b = parse("# B").slides[0].revision
        assertEquals(a1, a2)
        assertTrue(a1 != b)
    }

    @Test
    fun `empty document produces a single empty slide`() {
        // upstream: "".split() => [""], final slice(1) emits one slide with raw ""
        val md = parse("")
        assertEquals(1, md.slides.size)
        assertEquals("", md.slides[0].raw)
    }

    @Test
    fun `unclosed frontmatter runs to end of file`() {
        val md = parse("# One\n---\nlayout: center\nno closing dashes")
        assertEquals(2, md.slides.size)
        val second = md.slides[1]
        // upstream: scan for closing --- hits EOF; contentStart = lines.size + 1
        assertEquals(1, second.start)
        assertEquals(5, second.contentStart)
    }

    @Test
    fun `default layout example from slidev docs`() {
        val md = parse(
            """
            ---
            theme: seriph
            background: https://cover.sli.dev
            title: Welcome to Slidev
            ---

            # Welcome to Slidev

            Presentation slides for developers

            ---
            transition: fade-out
            ---

            # What is Slidev?

            Slidev is a slides maker.

            ---

            # Navigation

            Hover on the bottom-left corner

            <!-- this is the speaker note -->
            """.trimIndent(),
        )
        assertEquals(3, md.slides.size)
        assertEquals("Welcome to Slidev", md.slides[0].title)
        assertEquals("seriph", md.slides[0].frontmatter["theme"])
        assertEquals("What is Slidev?", md.slides[1].title)
        assertEquals("fade-out", md.slides[1].frontmatter["transition"])
        assertEquals("Navigation", md.slides[2].title)
        assertEquals("this is the speaker note", md.slides[2].note)
    }

    @Test
    fun `html comment state helper`() {
        assertTrue(!advanceHtmlCommentState("no comments here", false))
        assertTrue(advanceHtmlCommentState("<!-- open", false))
        assertTrue(!advanceHtmlCommentState("<!-- closed -->", false))
        assertTrue(!advanceHtmlCommentState("still in --> out", true))
        assertTrue(advanceHtmlCommentState("still in", true))
        assertTrue(advanceHtmlCommentState("<!-- a --> <!-- b", false))
        assertTrue(!advanceHtmlCommentState("-->  <!-- a -->", true))
    }
}
