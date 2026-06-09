package dev.slidev.intellij.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the slide-content tag scanner: completion contexts up to a caret,
 * full tokens for hover/navigation, and the frontmatter/fence/comment exclusions.
 * `<caret>` in the fixture marks the offset under test.
 */
class SlidevSlideTagsTest {

    private fun caret(textWithCaret: String): Pair<String, Int> {
        val offset = textWithCaret.indexOf("<caret>")
        check(offset >= 0) { "fixture must contain <caret>" }
        return textWithCaret.replace("<caret>", "") to offset
    }

    private fun contextAt(textWithCaret: String): SlidevSlideTags.Context? {
        val (text, offset) = caret(textWithCaret)
        return SlidevSlideTags.contextAt(text, "slides.md", offset)
    }

    private fun tokenAt(textWithCaret: String): SlidevSlideTags.Token? {
        val (text, offset) = caret(textWithCaret)
        return SlidevSlideTags.tokenAt(text, "slides.md", offset)
    }

    // ---------------------------------------------------------------- contexts

    @Test
    fun `tag name context after opening angle`() {
        val context = contextAt("# Title\n\n<Twe<caret>") as SlidevSlideTags.Context.TagName
        assertEquals("Twe", context.prefix)
        assertFalse(context.closing)
    }

    @Test
    fun `empty tag name context right after the angle`() {
        val context = contextAt("text <<caret>") as SlidevSlideTags.Context.TagName
        assertEquals("", context.prefix)
    }

    @Test
    fun `closing tag context`() {
        val context = contextAt("<Toc>\n</To<caret>") as SlidevSlideTags.Context.TagName
        assertEquals("To", context.prefix)
        assertTrue(context.closing)
    }

    @Test
    fun `lone angle in prose is not a tag`() {
        assertNull(contextAt("a < b <caret>"))
        assertNull(contextAt("a <2x<caret>"))
    }

    @Test
    fun `autolink is not a tag`() {
        assertNull(contextAt("<https://sli.dev> <caret>x"))
    }

    @Test
    fun `attribute context after the tag name`() {
        val context = contextAt("<Tweet <caret>") as SlidevSlideTags.Context.AttributeName
        assertEquals("Tweet", context.tagName)
        assertEquals("", context.prefix)
    }

    @Test
    fun `attribute prefix includes vue sigils`() {
        val context = contextAt("<Tweet id=\"1\" :sc<caret>") as SlidevSlideTags.Context.AttributeName
        assertEquals("Tweet", context.tagName)
        assertEquals(":sc", context.prefix)
        assertEquals("v-cl", (contextAt("<div v-cl<caret>") as SlidevSlideTags.Context.AttributeName).prefix)
    }

    @Test
    fun `attribute context spans a multi-line tag`() {
        val context = contextAt("<Tweet\n  id=\"1\"\n  sc<caret>") as SlidevSlideTags.Context.AttributeName
        assertEquals("Tweet", context.tagName)
        assertEquals("sc", context.prefix)
    }

    @Test
    fun `no context inside attribute values or after equals`() {
        assertNull(contextAt("<Tweet id=\"ab<caret>"))
        assertNull(contextAt("<Tweet id=<caret>"))
        assertNull(contextAt("<Tweet /<caret>"))
    }

    @Test
    fun `no context after a closed tag`() {
        assertNull(contextAt("<Tweet id=\"1\" /> te<caret>"))
    }

    @Test
    fun `no context in frontmatter blocks`() {
        assertNull(contextAt("---\ntheme: <Twe<caret>\n---\n\n# A"))
        assertNull(contextAt("# A\n\n---\nlayout: <T<caret>\n---\n\nb"))
    }

    @Test
    fun `no context in fenced code blocks`() {
        assertNull(contextAt("# A\n\n```html\n<Twe<caret>\n```\n"))
    }

    @Test
    fun `no context in html comments`() {
        assertNull(contextAt("# A\n\n<!-- note with <Twe<caret> -->"))
    }

    @Test
    fun `tag context resumes below a frontmatter block`() {
        val context = contextAt("---\ntheme: default\n---\n\n<Twe<caret>") as SlidevSlideTags.Context.TagName
        assertEquals("Twe", context.prefix)
    }

    // ---------------------------------------------------------------- tokens

    @Test
    fun `tag token under the name`() {
        val token = tokenAt("# A\n\n<Twe<caret>et id=\"1\" />") as SlidevSlideTags.Token.Tag
        assertEquals("Tweet", token.name)
        assertFalse(token.closing)
    }

    @Test
    fun `closing tag token`() {
        val token = tokenAt("<Toc>\n</To<caret>c>") as SlidevSlideTags.Token.Tag
        assertEquals("Toc", token.name)
        assertTrue(token.closing)
    }

    @Test
    fun `attribute token carries its tag`() {
        val token = tokenAt("<Tweet i<caret>d=\"1\" />") as SlidevSlideTags.Token.Attribute
        assertEquals("Tweet", token.tagName)
        assertEquals("id", token.name)
    }

    @Test
    fun `directive attribute token on a plain html tag`() {
        val token = tokenAt("<div v-cli<caret>ck=\"2\">x</div>") as SlidevSlideTags.Token.Attribute
        assertEquals("v-click", token.name)
    }

    @Test
    fun `no token inside attribute values`() {
        assertNull(tokenAt("<Tweet id=\"a<caret>b\" />"))
        assertNull(tokenAt("<Tweet id=a<caret>b />"))
    }

    @Test
    fun `no token in code fences comments or inline code`() {
        assertNull(tokenAt("```html\n<Twe<caret>et />\n```"))
        assertNull(tokenAt("<!-- <Twe<caret>et /> -->"))
        assertNull(tokenAt("see `<Twe<caret>et />` for details"))
    }

    @Test
    fun `no token on autolinks`() {
        assertNull(tokenAt("<htt<caret>ps://sli.dev>"))
    }

    // ---------------------------------------------------------------- regions

    @Test
    fun `isSlideContent excludes frontmatter and fences`() {
        val text = "---\ntheme: default\n---\n\n# A\n\n```ts\nlet x\n```\n\ntail"
        assertFalse(SlidevSlideTags.isSlideContent(text, "slides.md", text.indexOf("theme")))
        assertFalse(SlidevSlideTags.isSlideContent(text, "slides.md", text.indexOf("let x")))
        assertTrue(SlidevSlideTags.isSlideContent(text, "slides.md", text.indexOf("# A")))
        assertTrue(SlidevSlideTags.isSlideContent(text, "slides.md", text.indexOf("tail")))
    }
}
