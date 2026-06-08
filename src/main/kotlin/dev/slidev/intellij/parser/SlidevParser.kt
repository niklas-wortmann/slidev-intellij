package dev.slidev.intellij.parser

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Kotlin port of `@slidev/parser` (`packages/parser/src/core.ts`), `parseSync` path.
 * The splitting rules are replicated exactly:
 *  - a line starting with `---` ends the current slide;
 *  - a separator that is exactly `---` (not `----`) followed by a non-blank line opens
 *    a frontmatter block running to the next line that trims to `---`;
 *  - fenced code blocks (tracked by their leading-backtick run) are skipped, an unclosed
 *    fence swallows the rest of the file;
 *  - separators inside multi-line `<!-- -->` HTML comments are ignored.
 */
object SlidevParser {
    private val RE_FRONTMATTER = Regex("^---.*\\r?\\n([\\s\\S]*?)---")
    private val RE_YAML_CODEBLOCK = Regex("^\\s*```ya?ml([\\s\\S]*?)```")
    private val RE_HEADING = Regex("^(#+) (.*)$", RegexOption.MULTILINE)
    private val RE_LEADING_BACKTICKS = Regex("^\\s*`+")
    private val RE_HTML_COMMENT = Regex("<!--([\\s\\S]*?)-->")

    fun parse(markdown: String, filepath: String, preserveCR: Boolean = false): SlidevMarkdown {
        val lines = markdown.split(if (preserveCR) Regex("\n") else Regex("\r?\n"))
        val slides = mutableListOf<SourceSlide>()

        var start = 0
        var contentStart = 0
        var inHtmlComment = false

        fun slice(end: Int) {
            if (start == end) {
                return
            }
            val raw = lines.subList(start, end).joinToString("\n")
            slides.add(
                parseSlide(raw).toSourceSlide(
                    filepath = filepath,
                    index = slides.size,
                    start = start,
                    contentStart = contentStart,
                    end = end,
                ),
            )
            start = end + 1
            contentStart = end + 1
        }

        var i = 0
        while (i < lines.size) {
            val rawLine = lines[i]
            val line = rawLine.trimEnd()
            if (inHtmlComment) {
                inHtmlComment = advanceHtmlCommentState(rawLine, true)
                i++
                continue
            }

            if (line.startsWith("---")) {
                slice(i)

                val next = lines.getOrNull(i + 1)
                // found frontmatter, skip next dash
                if (line.getOrNull(3) != '-' && !next.isNullOrBlank()) {
                    start = i
                    i++
                    while (i < lines.size) {
                        if (lines[i].trimEnd() == "---") {
                            break
                        }
                        i++
                    }
                    contentStart = i + 1
                }
            }
            // skip code block
            else if (line.trimStart().startsWith("```")) {
                val codeBlockLevel = RE_LEADING_BACKTICKS.find(line)!!.value
                var j = i + 1
                while (j < lines.size) {
                    if (lines[j].startsWith(codeBlockLevel)) {
                        break
                    }
                    j++
                }
                // Update i only when code block ends
                if (j != lines.size) {
                    i = j
                }
            }
            else {
                inHtmlComment = advanceHtmlCommentState(rawLine, false)
            }
            i++
        }

        if (start <= lines.size - 1) {
            slice(lines.size)
        }

        return SlidevMarkdown(filepath, markdown, slides)
    }

    internal data class ParsedSlide(
        val raw: String,
        val title: String?,
        val level: Int?,
        val content: String,
        val frontmatter: Map<String, Any?>,
        val frontmatterStyle: FrontmatterStyle?,
        val frontmatterRaw: String?,
        val note: String?,
        val revision: String,
    ) {
        fun toSourceSlide(filepath: String, index: Int, start: Int, contentStart: Int, end: Int) = SourceSlide(
            raw = raw,
            title = title,
            level = level,
            content = content,
            frontmatter = frontmatter,
            frontmatterStyle = frontmatterStyle,
            frontmatterRaw = frontmatterRaw,
            note = note,
            revision = revision,
            filepath = filepath,
            index = index,
            start = start,
            contentStart = contentStart,
            end = end,
        )
    }

    internal fun parseSlide(raw: String): ParsedSlide {
        val matter = matter(raw)
        val frontmatter = matter.data
        var content = matter.content.trim()
        val revision = hash(raw.trim())

        var note: String? = null
        val comments = RE_HTML_COMMENT.findAll(content).toList()
        if (comments.isNotEmpty()) {
            val last = comments.last()
            if (last.range.first + last.value.length >= content.length) {
                note = last.groupValues[1].trim()
                content = content.substring(0, last.range.first).trim()
            }
        }

        var title: String?
        var level: Int? = null
        val frontmatterTitle = jsTruthyToString(frontmatter["title"]) ?: jsTruthyToString(frontmatter["name"])
        if (frontmatterTitle != null) {
            title = frontmatterTitle
        }
        else {
            val match = RE_HEADING.find(content)
            title = match?.groupValues?.get(2)?.trim()
            level = match?.groupValues?.get(1)?.length
        }
        val frontmatterLevel = frontmatter["level"]
        if (jsTruthy(frontmatterLevel) && frontmatterLevel is Number) {
            level = frontmatterLevel.toInt()
        }

        return ParsedSlide(
            raw = raw,
            title = title,
            level = level,
            content = content,
            frontmatter = frontmatter,
            frontmatterStyle = matter.type,
            frontmatterRaw = matter.raw,
            note = note,
            revision = revision,
        )
    }

    private data class MatterResult(
        val type: FrontmatterStyle?,
        val raw: String?,
        val data: Map<String, Any?>,
        val content: String,
    )

    private fun matter(code: String): MatterResult {
        var type: FrontmatterStyle? = null
        var raw: String? = null
        var content = code

        val frontmatterMatch = RE_FRONTMATTER.find(code)
        if (frontmatterMatch != null) {
            type = FrontmatterStyle.FRONTMATTER
            raw = frontmatterMatch.groupValues[1]
            content = code.removeRange(frontmatterMatch.range)
        }
        else {
            val yamlMatch = RE_YAML_CODEBLOCK.find(code)
            if (yamlMatch != null) {
                type = FrontmatterStyle.YAML
                raw = yamlMatch.groupValues[1]
                content = code.removeRange(yamlMatch.range)
            }
        }

        val data = raw?.let(::parseYaml)
        @Suppress("UNCHECKED_CAST")
        val map = (data as? Map<Any?, Any?>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
        return MatterResult(type, raw, map, content)
    }

    private fun parseYaml(raw: String): Any? = try {
        // SafeConstructor: never instantiate arbitrary classes from untrusted markdown.
        Yaml(SafeConstructor(LoaderOptions())).load(raw)
    }
    catch (_: Exception) {
        null
    }

    private fun jsTruthyToString(value: Any?): String? = if (jsTruthy(value)) value.toString() else null

    /** Same rolling hash as upstream: 32-bit wrapping, base-36, first 12 chars. */
    private fun hash(str: String): String {
        var hash = 0
        for (c in str) {
            hash = (hash shl 5) - hash + c.code
        }
        return hash.toString(36).take(12)
    }
}

/**
 * Tracks whether the cursor is inside a `<!-- -->` HTML comment after scanning [line].
 * Direct port of `advanceHtmlCommentState` from the upstream parser.
 */
internal fun advanceHtmlCommentState(line: String, inHtmlComment: Boolean): Boolean {
    var inComment = inHtmlComment
    var cursor = 0

    while (cursor < line.length) {
        if (inComment) {
            val end = line.indexOf("-->", cursor)
            if (end < 0) {
                return true
            }
            inComment = false
            cursor = end + 3
        }
        else {
            val start = line.indexOf("<!--", cursor)
            if (start < 0) {
                return false
            }
            val end = line.indexOf("-->", start + 4)
            if (end < 0) {
                return true
            }
            cursor = end + 3
        }
    }

    return inComment
}
