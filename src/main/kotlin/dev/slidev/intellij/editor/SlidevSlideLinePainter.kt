package dev.slidev.intellij.editor

import com.intellij.openapi.editor.EditorLinePainter
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.parser.FrontmatterStyle
import dev.slidev.intellij.parser.SourceSlide
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.settings.SlidevSettings

/**
 * Appends the resolved slide numbers after each slide divider line, the counterpart of
 * the slide-number decorations of `annotations.ts` in the VS Code extension: ` #2` or
 * ` #2-5` (consecutive numbers merged), or ` (hidden)` when the slide is never rendered.
 * Painted from the debounced service data; runs on the EDT during painting, so it only
 * does cheap map lookups and a linear scan over the slides.
 */
internal class SlidevSlideLinePainter : EditorLinePainter() {

    override fun getLineExtensions(
        project: Project,
        file: VirtualFile,
        lineNumber: Int,
    ): Collection<LineExtensionInfo>? {
        if (!SlidevSettings.getInstance(project).state.annotations) {
            return null
        }
        if (!file.name.endsWith(".md", ignoreCase = true)) {
            return null
        }
        val service = SlidevProjectService.getInstance(project)
        val md = service.markdownFor(file.path) ?: return null
        val slide = md.slides.firstOrNull { dividerLine(it) == lineNumber } ?: return null
        val data = service.stateContaining(file.path)?.data ?: return null

        val numbers = data.slides
            .filter { resolved ->
                sameSlide(resolved.source, slide) || resolved.importChain?.any { sameSlide(it, slide) } == true
            }
            .map { it.no }
        val text = if (numbers.isEmpty()) {
            " " + SlidevBundle.message("annotations.hidden")
        }
        else {
            " " + mergeRanges(numbers)
        }
        return listOf(LineExtensionInfo(text, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES.toTextAttributes()))
    }

    /** The `---` line above the slide; a frontmatter slide's own opening `---` doubles as it. */
    private fun dividerLine(slide: SourceSlide): Int =
        maxOf(0, if (slide.frontmatterStyle == FrontmatterStyle.FRONTMATTER) slide.start else slide.start - 1)

    private fun sameSlide(a: SourceSlide, b: SourceSlide): Boolean =
        a.filepath == b.filepath && a.index == b.index

    /** `[1, 2, 3, 5]` &rarr; `"#1-3, #5"`, like the VS Code decorations. */
    private fun mergeRanges(numbers: List<Int>): String {
        val parts = mutableListOf<String>()
        var start = numbers.first()
        var prev = start
        for (no in numbers.drop(1) + null) {
            if (no != null && no == prev + 1) {
                prev = no
                continue
            }
            parts.add(if (start == prev) "#$start" else "#$start-$prev")
            if (no != null) {
                start = no
                prev = no
            }
        }
        return parts.joinToString(", ")
    }
}
