package dev.slidev.intellij.parser

/**
 * Port of `stringify`/`stringifySlide` from `@slidev/parser/core`, used to write a
 * reordered slide list back to markdown. Takes a plain slide list (not a
 * [SlidevMarkdown]) because reordering produces new lists.
 */
object SlidevStringifier {

    fun stringify(slides: List<SourceSlide>): String =
        slides.mapIndexed(::stringifySlide).joinToString("\n").trim() + "\n"

    /**
     * The first slide and slides whose raw text already opens with a `---` frontmatter
     * block are emitted verbatim; everything else gets a `---` separator plus the blank
     * line the upstream `ensurePrefix('\n', raw)` guarantees.
     */
    fun stringifySlide(index: Int, slide: SourceSlide): String =
        if (slide.raw.startsWith("---") || index == 0) {
            slide.raw
        }
        else {
            "---\n" + if (slide.raw.startsWith("\n")) slide.raw else "\n" + slide.raw
        }
}
