package dev.slidev.intellij.core

import dev.slidev.intellij.parser.LoadedSlidevData
import dev.slidev.intellij.parser.ResolvedSlide
import dev.slidev.intellij.parser.SlidevMarkdown
import dev.slidev.intellij.parser.SourceSlide

/**
 * Pure caret-to-slide mapping helpers, ports of `useFocusedSlide` and
 * `getFirstDisplayedChild` from the VS Code extension.
 * Lines are 0-based throughout; this package stays free of IntelliJ Platform imports.
 */
object SlideNavigation {

    /**
     * The source slide whose range contains [line]. Separator lines between two slides
     * belong to neither range and map to the following slide; lines past the last slide
     * clamp to it.
     */
    fun sourceSlideForLine(md: SlidevMarkdown, line: Int): SourceSlide? =
        md.slides.firstOrNull { line < it.end } ?: md.slides.lastOrNull()

    /**
     * The slide of the resolved deck displayed when the caret is at [line] of [filepath].
     * Works inside imported files (matched through `markdownFiles`), and maps a `src:`
     * importing slide to the first slide rendered through it.
     */
    fun resolvedSlideForLine(data: LoadedSlidevData, filepath: String, line: Int): ResolvedSlide? {
        val md = data.markdownFiles[filepath] ?: return null
        val source = sourceSlideForLine(md, line) ?: return null
        return resolvedSlideFor(data, source)
    }

    /** First resolved slide displaying [source] — itself, or the first slide imported through it. */
    fun resolvedSlideFor(data: LoadedSlidevData, source: SourceSlide): ResolvedSlide? =
        data.slides.firstOrNull { resolved ->
            sameSlide(resolved.source, source) || resolved.importChain?.any { sameSlide(it, source) } == true
        }

    /** Content start line of the slide before the one containing [line], or null on the first slide. */
    fun prevSlideContentStart(md: SlidevMarkdown, line: Int): Int? =
        md.slides.getOrNull(slideIndexForLine(md, line) - 1)?.contentStart

    /** Content start line of the slide after the one containing [line], or null on the last slide. */
    fun nextSlideContentStart(md: SlidevMarkdown, line: Int): Int? {
        val index = slideIndexForLine(md, line)
        return if (index < 0) null else md.slides.getOrNull(index + 1)?.contentStart
    }

    private fun slideIndexForLine(md: SlidevMarkdown, line: Int): Int {
        val index = md.slides.indexOfFirst { line < it.end }
        return if (index >= 0) index else md.slides.lastIndex
    }

    private fun sameSlide(a: SourceSlide, b: SourceSlide): Boolean =
        a.filepath == b.filepath && a.index == b.index
}
