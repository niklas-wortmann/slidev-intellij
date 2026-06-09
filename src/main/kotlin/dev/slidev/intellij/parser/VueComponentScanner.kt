package dev.slidev.intellij.parser

/**
 * Lightweight scanner for user/theme/addon `.vue` components (plan.md, 13.2): the tag
 * name comes from the file name (PascalCase, mirroring `unplugin-vue-components`), the
 * props from a best-effort `defineProps` extraction over the raw text — both the
 * type-literal form (`defineProps<{ a?: string }>()`, also inside `withDefaults`) and
 * the object form (`defineProps({ a: { required: true } })`). No JS-plugin dependency.
 * This package must stay free of IntelliJ Platform imports.
 */
object VueComponentScanner {

    /** One extracted prop; [type] is the raw type text of the type-literal form, null otherwise. */
    data class VueProp(val name: String, val type: String?, val required: Boolean)

    data class VueComponent(val name: String, val props: List<VueProp>)

    private val NAME_PART = Regex("[-_. ]+")
    private val TYPE_ENTRY = Regex("^(?:readonly\\s+)?([A-Za-z_$][\\w$]*)(\\?)?\\s*:\\s*(.+)$", RegexOption.DOT_MATCHES_ALL)
    private val OBJECT_KEY = Regex("^['\"]?([A-Za-z_$][\\w$-]*)['\"]?$")

    /** `my-component.vue` / `foo_bar.vue` / `MyComponent.vue` → `MyComponent` (filename-based). */
    fun componentName(fileName: String): String =
        fileName.removeSuffix(".vue")
            .split(NAME_PART)
            .filter { it.isNotEmpty() }
            .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }

    fun scan(fileName: String, text: String): VueComponent =
        VueComponent(componentName(fileName), props(text))

    fun props(text: String): List<VueProp> {
        val at = text.indexOf("defineProps")
        if (at < 0) {
            return emptyList()
        }
        var i = skipWhitespace(text, at + "defineProps".length)
        if (i < text.length && text[i] == '<') {
            // defineProps<{ ... }>() — the type-literal form.
            val open = text.indexOf('{', i)
            if (open < 0) {
                return emptyList()
            }
            val close = matchBrace(text, open) ?: return emptyList()
            return typeLiteralProps(text.substring(open + 1, close))
        }
        if (i < text.length && text[i] == '(') {
            i = skipWhitespace(text, i + 1)
            if (i < text.length && text[i] == '{') {
                val close = matchBrace(text, i) ?: return emptyList()
                return objectLiteralProps(text.substring(i + 1, close))
            }
        }
        return emptyList()
    }

    /** `pos?: string; markdownSource?: Foo` → props with optionality and raw type text. */
    private fun typeLiteralProps(body: String): List<VueProp> =
        splitTopLevel(body, setOf(';', ',', '\n')).mapNotNull { entry ->
            val match = TYPE_ENTRY.find(entry.trim()) ?: return@mapNotNull null
            val (name, optional, type) = match.destructured
            VueProp(name, type.trim().ifEmpty { null }, required = optional.isEmpty())
        }

    /** `{ a: { required: true }, b: String }` → top-level keys; `required: true` is honored. */
    private fun objectLiteralProps(body: String): List<VueProp> =
        splitTopLevel(body, setOf(',')).mapNotNull { entry ->
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) {
                return@mapNotNull null
            }
            val colon = topLevelColon(trimmed)
            val key = (if (colon < 0) trimmed else trimmed.take(colon)).trim()
            val name = OBJECT_KEY.find(key)?.groupValues?.get(1) ?: return@mapNotNull null
            val value = if (colon < 0) "" else trimmed.substring(colon + 1)
            VueProp(name, type = null, required = Regex("required\\s*:\\s*true").containsMatchIn(value))
        }

    // ---------------------------------------------------------------- text helpers

    private fun isIdentChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'

    private fun skipWhitespace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) {
            i++
        }
        return i
    }

    /** Index of the first `:` outside nested braces/brackets/strings, or -1. */
    private fun topLevelColon(text: String): Int {
        var depth = 0
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '{', '[', '(' -> depth++
                '}', ']', ')' -> depth--
                '\'', '"', '`' -> i = skipString(text, i)
                ':' -> if (depth == 0) return i
                '/' -> i = skipComment(text, i)
            }
            i++
        }
        return -1
    }

    /** Splits [body] on top-level [separators], honoring nested braces, strings, and comments. */
    private fun splitTopLevel(body: String, separators: Set<Char>): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                // `<` only opens a generic after an identifier — `(x) => y` must not unbalance.
                c == '{' || c == '[' || c == '(' || (c == '<' && i > 0 && isIdentChar(body[i - 1])) -> {
                    depth++
                    current.append(c)
                }
                c == '}' || c == ']' || c == ')' || (c == '>' && depth > 0 && (i == 0 || body[i - 1] != '=')) -> {
                    depth--
                    current.append(c)
                }
                c == '\'' || c == '"' || c == '`' -> {
                    val end = skipString(body, i)
                    current.append(body, i, minOf(end + 1, body.length))
                    i = end
                }
                c == '/' -> {
                    val end = skipComment(body, i)
                    if (end == i) {
                        current.append(c)
                    }
                    i = end
                }
                depth == 0 && c in separators -> {
                    if (current.isNotBlank()) {
                        parts.add(current.toString())
                    }
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotBlank()) {
            parts.add(current.toString())
        }
        return parts
    }

    /** Returns the matching `}` index for the `{` at [open], skipping strings and comments. */
    private fun matchBrace(text: String, open: Int): Int? {
        var depth = 0
        var i = open
        while (i < text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return i
                    }
                }
                '\'', '"', '`' -> i = skipString(text, i)
                '/' -> i = skipComment(text, i)
            }
            i++
        }
        return null
    }

    /** Index of the closing quote of the string starting at [start] (best effort, handles `\\`). */
    private fun skipString(text: String, start: Int): Int {
        val quote = text[start]
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i++
                quote -> return i
            }
            i++
        }
        return text.length - 1
    }

    /** Index of the last char of a `//` or `/* */` comment starting at [start], or [start]. */
    private fun skipComment(text: String, start: Int): Int {
        if (start + 1 >= text.length) {
            return start
        }
        return when (text[start + 1]) {
            '/' -> text.indexOf('\n', start).let { if (it < 0) text.length - 1 else it }
            '*' -> text.indexOf("*/", start + 2).let { if (it < 0) text.length - 1 else it + 1 }
            else -> start
        }
    }
}
