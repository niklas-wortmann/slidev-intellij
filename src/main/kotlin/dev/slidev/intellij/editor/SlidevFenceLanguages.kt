package dev.slidev.intellij.editor

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage

/**
 * Resolves a code-fence language token to an injectable [Language] (plan.md, 19.3).
 * The markdown plugin's own `CodeFenceLanguageGuesser` is `@ApiStatus.Internal` (the
 * 12.2 verifier lesson), so the lookup is replicated: alias table for the common Slidev
 * tokens → case-insensitive language-ID match → file-type-by-extension fallback. A token
 * whose language is missing (e.g. `vue` without the Vue plugin) resolves to null — the
 * caller skips injection, never errors.
 */
internal object SlidevFenceLanguages {

    fun resolve(token: String): Language? {
        val normalized = token.trim().lowercase()
        if (normalized.isEmpty()) return null
        val language = ALIASES[normalized]?.let(::findLanguageById)
            ?: findLanguageById(normalized)
            ?: languageByExtension(normalized)
        return language?.takeIf { it !== PlainTextLanguage.INSTANCE && LanguageUtil.isInjectableLanguage(it) }
    }

    private fun findLanguageById(id: String): Language? =
        Language.findLanguageByID(id)
            ?: Language.getRegisteredLanguages().firstOrNull { it.id.equals(id, ignoreCase = true) }

    private fun languageByExtension(extension: String): Language? =
        (FileTypeManager.getInstance().getFileTypeByExtension(extension) as? LanguageFileType)?.language

    // Common Slidev fence tokens → registered language IDs. Everything else falls
    // through to the ID/extension lookups (e.g. `kotlin`, `rust`, `go`).
    private val ALIASES = mapOf(
        "js" to "JavaScript",
        "javascript" to "JavaScript",
        "mjs" to "JavaScript",
        "cjs" to "JavaScript",
        "ts" to "TypeScript",
        "typescript" to "TypeScript",
        "mts" to "TypeScript",
        "cts" to "TypeScript",
        "tsx" to "TypeScript JSX",
        "jsx" to "ECMAScript 6",
        "vue" to "Vue",
        "html" to "HTML",
        "css" to "CSS",
        "scss" to "SCSS",
        "sass" to "SASS",
        "less" to "LESS",
        "json" to "JSON",
        "yaml" to "yaml",
        "yml" to "yaml",
        "md" to "Markdown",
        "markdown" to "Markdown",
        "sh" to "Shell Script",
        "bash" to "Shell Script",
        "shell" to "Shell Script",
        "zsh" to "Shell Script",
        "xml" to "XML",
        "svg" to "SVG",
        "py" to "Python",
        "python" to "Python",
    )
}
