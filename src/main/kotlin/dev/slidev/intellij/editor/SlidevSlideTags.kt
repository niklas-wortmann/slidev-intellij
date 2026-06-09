package dev.slidev.intellij.editor

import dev.slidev.intellij.parser.SlidevParser
import dev.slidev.intellij.parser.findCodeBlocks

/**
 * Locates component/HTML tags in slide *content* — everything outside frontmatter
 * blocks and fenced code — and the tag/attribute context at a caret position, shared
 * by the component completion, documentation, and navigation (plan.md, Phase 14).
 * Inline tags are scattered `HTML_TAG` tokens in the Markdown PSI, so like
 * [SlidevFrontmatterBlocks] this works on text offsets and re-parses fresh on each
 * request. Pure logic, no platform imports, so it is unit-testable.
 *
 * Scanning is bounded to the caret's paragraph (contiguous non-blank content lines):
 * Markdown never carries an inline tag across a blank line, and that keeps the
 * per-keystroke cost flat. Speaker notes (`<!-- ... -->`) and closed backtick code
 * spans are skipped.
 */
internal object SlidevSlideTags {

    /** The caret's typing position for completion, scanned up to the caret only. */
    sealed class Context {
        /** Caret typing a tag name right after `<` (or `</` when [closing]). */
        class TagName(val prefix: String, val closing: Boolean) : Context()

        /** Caret in attribute-name position inside an open `<`[tagName] tag. */
        class AttributeName(val tagName: String, val prefix: String) : Context()
    }

    /** A complete token, for hover documentation, navigation, and highlighting. */
    sealed class Token {
        abstract val range: IntRange

        /** A tag name; [range] covers the name only, not the `<`/`</`. */
        class Tag(val name: String, override val range: IntRange, val closing: Boolean) : Token()

        /** An attribute name inside `<`[tagName], including `:`/`@`/`v-` sigils. */
        class Attribute(val tagName: String, val name: String, override val range: IntRange) : Token()
    }

    /** Whether [offset] lies in slide content (not frontmatter, not fenced code). */
    fun isSlideContent(text: String, filepath: String, offset: Int): Boolean {
        val doc = DocText(text, filepath)
        return doc.isContentLine(doc.lineOf(offset.coerceIn(0, text.length)))
    }

    /** The completion context at [offset], or null when no completion applies there. */
    fun contextAt(text: String, filepath: String, offset: Int): Context? {
        val doc = DocText(text, filepath)
        val caret = offset.coerceIn(0, text.length)
        val line = doc.lineOf(caret)
        if (!doc.isContentLine(line)) {
            return null
        }

        var i = doc.paragraphStartOffset(line)
        var tagName: String? = null // non-null while inside an open tag's attribute area
        while (i < caret) {
            val c = text[i]
            if (tagName == null) {
                when {
                    c == '<' && text.startsWith("<!--", i) -> {
                        val close = text.indexOf("-->", i + 4)
                        if (close == -1 || close + 3 > caret) {
                            return null // caret inside a comment (speaker note)
                        }
                        i = close + 3
                    }

                    c == '<' -> {
                        var j = i + 1
                        val closing = j < caret && text[j] == '/'
                        if (closing) {
                            j++
                        }
                        val nameStart = j
                        while (j < caret && isTagNameChar(text[j])) {
                            j++
                        }
                        val name = text.substring(nameStart, j)
                        if (j == caret && (name.isEmpty() || name.first().isLetter())) {
                            // `<Twe<caret>` — the caret is typing this tag's name.
                            return Context.TagName(name, closing)
                        }
                        when {
                            // `a < b`, `<2x`, `<https://…` — not a tag, keep scanning as text.
                            name.isEmpty() || !name.first().isLetter() -> i = maxOf(j, i + 1)
                            text[j] == '>' -> i = j + 1
                            text[j] == '/' || text[j].isWhitespace() -> {
                                tagName = name
                                i = j
                            }

                            else -> i = j
                        }
                    }

                    c == '`' -> {
                        val close = text.indexOf('`', i + 1)
                        // Skip closed inline code; an unclosed backtick is literal text.
                        i = if (close != -1 && close < caret) close + 1 else i + 1
                    }

                    else -> i++
                }
            }
            else {
                when {
                    c == '"' || c == '\'' -> {
                        val close = text.indexOf(c, i + 1)
                        if (close == -1 || close >= caret) {
                            return null // caret inside an attribute value
                        }
                        i = close + 1
                    }

                    c == '>' -> {
                        tagName = null
                        i++
                    }

                    else -> i++
                }
            }
        }

        val tag = tagName ?: return null
        var s = caret
        while (s > 0 && isAttrNameChar(text[s - 1])) {
            s--
        }
        if (s > 0 && (text[s - 1] == '=' || text[s - 1] == '/')) {
            return null // value position (`key=ba<caret>`) or right after a self-close slash
        }
        return Context.AttributeName(tag, text.substring(s, caret))
    }

    /** The tag-name or attribute-name token covering [offset], or null. */
    fun tokenAt(text: String, filepath: String, offset: Int): Token? {
        val doc = DocText(text, filepath)
        val target = offset.coerceIn(0, text.length)
        val line = doc.lineOf(target)
        if (!doc.isContentLine(line)) {
            return null
        }
        val tokens = mutableListOf<Token>()
        collectTokens(text, doc.paragraphStartOffset(line), doc.paragraphEndOffset(line), tokens)
        return tokens.firstOrNull { target in it.range.first..(it.range.last + 1) }
    }

    /** All tag/attribute tokens in slide content of [text], in document order. */
    fun tokens(text: String, filepath: String): List<Token> {
        val doc = DocText(text, filepath)
        val result = mutableListOf<Token>()
        for (paragraph in doc.paragraphRanges()) {
            collectTokens(text, paragraph.first, paragraph.last + 1, result)
        }
        return result
    }

    /**
     * Scans `[from, ceiling)` — one paragraph — into [sink]. Comments, inline code
     * spans, and attribute values yield no tokens; an unterminated comment or quote
     * swallows the rest of the paragraph, matching how the browser would parse it.
     */
    private fun collectTokens(text: String, from: Int, ceiling: Int, sink: MutableList<Token>) {
        var i = from
        var tagName: String? = null // non-null while inside an open tag's attribute area
        while (i < ceiling) {
            val c = text[i]
            if (tagName == null) {
                when {
                    c == '<' && text.startsWith("<!--", i) -> {
                        val close = text.indexOf("-->", i + 4)
                        if (close == -1) {
                            return // unclosed comment swallows the rest
                        }
                        i = close + 3
                    }

                    c == '<' -> {
                        var j = i + 1
                        val closing = j < ceiling && text[j] == '/'
                        if (closing) {
                            j++
                        }
                        val nameStart = j
                        while (j < ceiling && isTagNameChar(text[j])) {
                            j++
                        }
                        val name = text.substring(nameStart, j)
                        val next = text.getOrNull(j).takeIf { j < ceiling }
                        if (name.isEmpty() || !name.first().isLetter() ||
                            (next != null && next != '>' && next != '/' && !next.isWhitespace())
                        ) {
                            i = maxOf(j, i + 1) // not a tag
                            continue
                        }
                        sink.add(Token.Tag(name, nameStart until j, closing))
                        when (next) {
                            null -> return
                            '>' -> i = j + 1
                            else -> {
                                tagName = name
                                i = j
                            }
                        }
                    }

                    c == '`' -> {
                        val close = text.indexOf('`', i + 1)
                        // Skip closed inline code; an unclosed backtick is literal text.
                        i = if (close != -1 && close < ceiling) close + 1 else i + 1
                    }

                    else -> i++
                }
            }
            else {
                when {
                    c == '"' || c == '\'' -> {
                        val close = text.indexOf(c, i + 1)
                        if (close == -1 || close >= ceiling) {
                            return
                        }
                        i = close + 1
                    }

                    c == '>' -> {
                        tagName = null
                        i++
                    }

                    c == '=' -> {
                        i++
                        // Skip an unquoted value so it is not mistaken for an attribute name.
                        if (i < ceiling && text[i] != '"' && text[i] != '\'') {
                            while (i < ceiling && !text[i].isWhitespace() && text[i] != '>') {
                                i++
                            }
                        }
                    }

                    isAttrNameChar(c) -> {
                        var j = i
                        while (j < ceiling && isAttrNameChar(text[j])) {
                            j++
                        }
                        sink.add(Token.Attribute(tagName, text.substring(i, j), i until j))
                        i = j
                    }

                    else -> i++
                }
            }
        }
    }

    private fun isTagNameChar(c: Char): Boolean = c.isLetterOrDigit() || c == '-'

    /** Vue attribute names include binding/event/modifier sigils: `:x`, `@x`, `v-mark.red`. */
    private fun isAttrNameChar(c: Char): Boolean = c.isLetterOrDigit() || c in "-_:@."

    /** Line geometry plus the excluded (frontmatter/fence) line ranges of one text. */
    private class DocText(private val text: String, filepath: String) {
        val lines: List<String> = text.lines()
        private val lineStarts: IntArray = IntArray(lines.size).also { starts ->
            var offset = 0
            for ((index, line) in lines.withIndex()) {
                starts[index] = offset
                offset += line.length + 1
            }
        }
        private val excluded: List<IntRange> = buildList {
            val md = SlidevParser.parse(text, filepath)
            for (slide in md.slides) {
                slide.frontmatterLines?.let { add(it.first..minOf(it.last, lines.lastIndex)) }
            }
            for (block in findCodeBlocks(lines)) {
                // Include the fence lines themselves, not just the interior.
                add(block.startLine - 1..block.endLine)
            }
        }

        fun lineOf(offset: Int): Int {
            val search = lineStarts.toList().binarySearch(offset)
            return if (search >= 0) search else -search - 2
        }

        fun isContentLine(line: Int): Boolean = excluded.none { line in it }

        /** Start offset of the caret paragraph: contiguous non-blank content lines. */
        fun paragraphStartOffset(line: Int): Int {
            var first = line
            while (first > 0 && lines[first - 1].isNotBlank() && isContentLine(first - 1)) {
                first--
            }
            return lineStarts[first]
        }

        /** End offset (exclusive) of the caret paragraph. */
        fun paragraphEndOffset(line: Int): Int {
            var last = line
            while (last < lines.lastIndex && lines[last + 1].isNotBlank() && isContentLine(last + 1)) {
                last++
            }
            return lineStarts[last] + lines[last].length
        }

        /** Offset ranges of all content paragraphs, in document order. */
        fun paragraphRanges(): Sequence<IntRange> = sequence {
            var line = 0
            while (line < lines.size) {
                if (lines[line].isBlank() || !isContentLine(line)) {
                    line++
                    continue
                }
                var last = line
                while (last < lines.lastIndex && lines[last + 1].isNotBlank() && isContentLine(last + 1)) {
                    last++
                }
                yield(lineStarts[line] until lineStarts[last] + lines[last].length)
                line = last + 1
            }
        }
    }
}
