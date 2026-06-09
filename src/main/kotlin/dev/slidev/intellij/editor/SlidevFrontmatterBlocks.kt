package dev.slidev.intellij.editor

import dev.slidev.intellij.parser.SlidevParser

/**
 * Locates the classic `---` frontmatter blocks of a Slidev markdown text and the
 * key/value context at a caret position, shared by the frontmatter completion,
 * documentation, and validation. Like the folding builder, the text is re-parsed
 * fresh on each request because the service data is debounced. Pure logic, no
 * platform imports, so it is unit-testable.
 */
internal object SlidevFrontmatterBlocks {

    /**
     * One frontmatter block; [slideIndex] selects the schema (0 = headmatter),
     * [contentLines] are the 0-based YAML lines between the `---` delimiters
     * (empty range for an empty block).
     */
    data class Block(val slideIndex: Int, val contentLines: IntRange)

    /** The caret's position within a block: on a top-level key, or in a key's value. */
    sealed class Context(val block: Block) {
        /** Caret in key position; [prefix] is the typed part of the key before the caret. */
        class Key(block: Block, val prefix: String) : Context(block)

        /** Caret in the value of top-level [key]; [prefix] is the typed value part before the caret. */
        class Value(block: Block, val key: String, val prefix: String) : Context(block)
    }

    private val KEY_LINE = Regex("^([A-Za-z0-9_$-]+)\\s*:")

    fun blocks(text: String, filepath: String): List<Block> {
        val md = SlidevParser.parse(text, filepath)
        val lastLine = text.lineSequence().count() - 1
        return md.slides.mapNotNull { slide ->
            val lines = slide.frontmatterLines ?: return@mapNotNull null
            // An unclosed block runs to EOF; drop the closing `---` line when present.
            val lastContent = minOf(lines.last - 1, lastLine)
            Block(slide.index, lines.first + 1..lastContent)
        }
    }

    fun blockAt(blocks: List<Block>, line: Int): Block? =
        blocks.firstOrNull { line in it.contentLines }

    /** The 0-based line of top-level [key] within [block], or null. */
    fun keyLine(lines: List<String>, block: Block, key: String): Int? =
        block.contentLines.firstOrNull { line ->
            KEY_LINE.find(lines.getOrEmpty(line))?.groupValues?.get(1) == key
        }

    /** The top-level keys already present in [block], used to filter completions. */
    fun presentKeys(lines: List<String>, block: Block): Set<String> =
        block.contentLines.mapNotNullTo(mutableSetOf()) { line ->
            KEY_LINE.find(lines.getOrEmpty(line))?.groupValues?.get(1)
        }

    fun contextAt(lines: List<String>, blocks: List<Block>, line: Int, column: Int): Context? {
        val block = blockAt(blocks, line) ?: return null
        val text = lines.getOrEmpty(line)
        val beforeCaret = text.take(column)
        if (beforeCaret.startsWith(" ") || beforeCaret.startsWith("\t")) {
            return null // nested mapping — only top-level keys are schema-known
        }
        val colon = beforeCaret.indexOf(':')
        if (colon < 0) {
            return if (beforeCaret.all { it.isLetterOrDigit() || it in "_$-" }) {
                Context.Key(block, beforeCaret)
            }
            else {
                null
            }
        }
        val key = KEY_LINE.find(text)?.groupValues?.get(1) ?: return null
        return Context.Value(block, key, beforeCaret.substring(colon + 1).trimStart())
    }

    private fun List<String>.getOrEmpty(index: Int): String = getOrNull(index) ?: ""
}
