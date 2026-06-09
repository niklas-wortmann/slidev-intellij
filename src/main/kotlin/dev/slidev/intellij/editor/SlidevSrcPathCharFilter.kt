package dev.slidev.intellij.editor

import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup

/**
 * Keeps the completion popup open while a frontmatter `src:` path is being typed.
 * Typed characters reach the lookup through the charFilter chain before any
 * [com.intellij.codeInsight.completion.CompletionContributor] runs, and the platform's
 * `DefaultCharFilter` hides the lookup on anything that is not a Java identifier part —
 * so `/`, `.` and `-` would close the popup mid-path without this filter.
 */
internal class SlidevSrcPathCharFilter : CharFilter() {

    override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup): Result? {
        if (c != '/' && c != '.' && c != '-') {
            return null
        }
        val file = lookup.psiFile ?: return null
        val context = SlidevFrontmatterCompletionContributor.frontmatterContext(file, lookup.editor.caretModel.offset)
            ?: return null
        if (context is SlidevFrontmatterBlocks.Context.Value && context.key == "src") {
            return Result.ADD_TO_PREFIX
        }
        return null
    }
}
