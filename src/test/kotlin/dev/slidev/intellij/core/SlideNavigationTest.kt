package dev.slidev.intellij.core

import dev.slidev.intellij.parser.FileTextProvider
import dev.slidev.intellij.parser.SlidevDataLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlideNavigationTest {

    // Lines:  0 `# One`, 2-4 frontmatter of "Two" (content from 5), 8-10 `src:` slide,
    // 13.. "Last". Resolved deck: One, Two, Imported A, Imported B, Last.
    private val data = SlidevDataLoader.load(
        "/proj",
        "/proj/slides.md",
        FileTextProvider { path ->
            mapOf(
                "/proj/slides.md" to
                    "# One\n\n---\nlayout: cover\n---\n\n# Two\n\n---\nsrc: ./other.md\n---\n\n---\n\n# Last",
                "/proj/other.md" to "# Imported A\n\n---\n\n# Imported B",
            )[path]
        },
    )

    private val entry = data.entry

    @Test
    fun `caret line maps to the resolved slide number`() {
        assertEquals(1, SlideNavigation.resolvedSlideForLine(data, "/proj/slides.md", 0)?.no)
        assertEquals(2, SlideNavigation.resolvedSlideForLine(data, "/proj/slides.md", 3)?.no)
        assertEquals(2, SlideNavigation.resolvedSlideForLine(data, "/proj/slides.md", 6)?.no)
        assertEquals(5, SlideNavigation.resolvedSlideForLine(data, "/proj/slides.md", 14)?.no)
    }

    @Test
    fun `caret on a src importing slide maps to its first displayed child`() {
        assertEquals(3, SlideNavigation.resolvedSlideForLine(data, "/proj/slides.md", 9)?.no)
    }

    @Test
    fun `caret inside an imported file maps through markdownFiles`() {
        assertEquals(3, SlideNavigation.resolvedSlideForLine(data, "/proj/other.md", 0)?.no)
        assertEquals(4, SlideNavigation.resolvedSlideForLine(data, "/proj/other.md", 4)?.no)
    }

    @Test
    fun `separator gap lines map to the following slide`() {
        // Line 12 is the bare `---` between the src slide and "Last".
        assertEquals(5, SlideNavigation.resolvedSlideForLine(data, "/proj/slides.md", 12)?.no)
    }

    @Test
    fun `lines past the last slide clamp to it`() {
        assertEquals(5, SlideNavigation.resolvedSlideForLine(data, "/proj/slides.md", 999)?.no)
    }

    @Test
    fun `unknown files resolve to null`() {
        assertNull(SlideNavigation.resolvedSlideForLine(data, "/proj/unknown.md", 0))
    }

    @Test
    fun `prev and next navigate between content starts`() {
        assertEquals(5, SlideNavigation.nextSlideContentStart(entry, 0))
        assertEquals(11, SlideNavigation.nextSlideContentStart(entry, 6))
        assertEquals(0, SlideNavigation.prevSlideContentStart(entry, 6))
        assertEquals(5, SlideNavigation.prevSlideContentStart(entry, 9))
    }

    @Test
    fun `prev on the first slide and next on the last slide return null`() {
        assertNull(SlideNavigation.prevSlideContentStart(entry, 0))
        assertNull(SlideNavigation.nextSlideContentStart(entry, 14))
    }
}
