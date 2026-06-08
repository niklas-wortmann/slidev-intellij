package dev.slidev.intellij.core

import dev.slidev.intellij.parser.LoadedSlidevData
import dev.slidev.intellij.parser.SlidevStringifier
import dev.slidev.intellij.parser.SourceSlide

/**
 * Pure half of slide drag-and-drop reordering, the counterpart of `handleDrop` in
 * `slidesTree.ts` of the VS Code extension: the dragged slides are removed from every
 * markdown file of the deck and reinserted right after the target slide.
 */
object SlideReorder {

    /**
     * @param changes new full text per changed file path
     * @param insertedAt index of the first dragged slide in the target file's new slide list
     */
    data class MoveResult(
        val changes: Map<String, String>,
        val insertedAt: Int,
    )

    /**
     * Computes the file rewrites for inserting [dragged] after the slide at 0-based
     * [targetIndex] of [targetPath] (`targetIndex == -1` inserts at the front).
     * Returns null when nothing would change or the target file is unknown.
     */
    fun computeMove(
        data: LoadedSlidevData,
        dragged: List<SourceSlide>,
        targetPath: String,
        targetIndex: Int,
    ): MoveResult? {
        val targetMarkdown = data.markdownFiles[targetPath] ?: return null
        if (dragged.isEmpty()) {
            return null
        }

        fun isDragged(slide: SourceSlide) = dragged.any { sameSlide(it, slide) }

        val remaining = targetMarkdown.slides.map { if (isDragged(it)) null else it }
        val before = remaining.take(targetIndex + 1).filterNotNull()
        val after = remaining.drop(targetIndex + 1).filterNotNull()
        val newSlides = linkedMapOf(targetPath to (before + dragged + after))
        for ((path, md) in data.markdownFiles) {
            if (path == targetPath) {
                continue
            }
            val kept = md.slides.filterNot(::isDragged)
            if (kept.size != md.slides.size) {
                newSlides[path] = kept
            }
        }

        val changes = newSlides
            .mapValues { (_, slides) -> SlidevStringifier.stringify(slides) }
            .filter { (path, text) -> data.markdownFiles[path]?.raw?.let(::normalize) != normalize(text) }
        if (changes.isEmpty()) {
            return null
        }
        return MoveResult(changes, before.size)
    }

    fun sameSlide(a: SourceSlide, b: SourceSlide): Boolean =
        a.filepath == b.filepath && a.index == b.index

    /** Stringification trims the file tail, so compare modulo trailing whitespace. */
    private fun normalize(text: String): String = text.trimEnd() + "\n"
}
