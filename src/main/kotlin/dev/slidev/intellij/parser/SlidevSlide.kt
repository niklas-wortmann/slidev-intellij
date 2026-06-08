package dev.slidev.intellij.parser

/**
 * Data model for parsed Slidev markdown.
 * Kotlin port of the structures in `@slidev/types` used by `@slidev/parser`.
 * This package must stay free of IntelliJ Platform imports.
 */

enum class FrontmatterStyle {
    /** Classic `---` ... `---` YAML frontmatter. */
    FRONTMATTER,

    /** ```yaml fenced code block frontmatter. */
    YAML,
}

/**
 * A single slide as it appears in one markdown file (before import resolution).
 * Line numbers are 0-based; [start]/[end] follow the upstream parser semantics:
 * `raw == lines[start until end]`, [contentStart] is the first line after the
 * frontmatter block (or equal to [start] when there is none).
 */
data class SourceSlide(
    val raw: String,
    val title: String?,
    val level: Int?,
    val content: String,
    val frontmatter: Map<String, Any?>,
    val frontmatterStyle: FrontmatterStyle?,
    val frontmatterRaw: String?,
    val note: String?,
    val revision: String,
    val filepath: String,
    val index: Int,
    val start: Int,
    val contentStart: Int,
    val end: Int,
) {
    val isHidden: Boolean
        get() = jsTruthy(frontmatter["hide"]) || jsTruthy(frontmatter["disabled"])

    /**
     * 0-based line range of a classic frontmatter block, including the opening and
     * closing `---` lines, or null when the slide has no classic frontmatter.
     * Callers should clamp against the document — an unclosed block runs to EOF.
     */
    val frontmatterLines: IntRange?
        get() = if (frontmatterStyle == FrontmatterStyle.FRONTMATTER && contentStart > start) {
            start until contentStart
        }
        else {
            null
        }
}

/** One parsed markdown file. */
data class SlidevMarkdown(
    val filepath: String,
    val raw: String,
    val slides: List<SourceSlide>,
)

/** A parse/load problem attached to a 0-based row of a markdown file. */
data class SlideError(
    val row: Int,
    val message: String,
)

/** A slide in the flattened, import-resolved deck. [index] is 0-based, [no] is the 1-based slide number. */
data class ResolvedSlide(
    val frontmatter: Map<String, Any?>,
    val content: String,
    val note: String?,
    val title: String?,
    val level: Int?,
    val index: Int,
    val importChain: List<SourceSlide>?,
    val source: SourceSlide,
) {
    val no: Int get() = index + 1
}

/** Result of loading an entry file with all `src:` imports resolved. */
data class LoadedSlidevData(
    val slides: List<ResolvedSlide>,
    val entry: SlidevMarkdown,
    val headmatter: Map<String, Any?>,
    val markdownFiles: Map<String, SlidevMarkdown>,
    val errors: Map<String, List<SlideError>>,
)

/** JavaScript-style truthiness, used where the upstream parser relies on it. */
internal fun jsTruthy(value: Any?): Boolean = when (value) {
    null -> false
    is Boolean -> value
    is String -> value.isNotEmpty()
    is Number -> value.toDouble() != 0.0 && !value.toDouble().isNaN()
    else -> true
}
