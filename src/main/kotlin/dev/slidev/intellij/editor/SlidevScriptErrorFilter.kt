package dev.slidev.intellij.editor

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile

/**
 * Suppresses parse-error highlights inside the lexer-embedded JS PSI of slide `<script>`
 * bodies (plan.md, 18.2). That embedded parse is unreliable by construction: the markdown
 * template-data lexing swallows newlines into `MARKDOWN_OUTER_BLOCK` elements, so every
 * semicolon-less statement reports a phantom "Newline or semicolon expected", and a
 * `lang="ts"` body is parsed by the plain-JS dialect regardless (the 18.1 spike findings).
 * Silence beats phantom errors in a slide deck — genuine syntax feedback is out of the
 * feature's scope (highlighting + basic completion).
 */
internal class SlidevScriptErrorFilter : HighlightErrorFilter() {

    override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean {
        if (!isInsideEmbeddedScript(element)) return true
        val file = element.containingFile ?: return true
        return !SlidevScriptSupport.isSlidevSlideHtmlRoot(file)
    }

    private fun isInsideEmbeddedScript(element: PsiErrorElement): Boolean =
        generateSequence<PsiElement>(element) { it.parent }
            .takeWhile { it !is PsiFile }
            .any { SlidevScriptSupport.isJavaScriptKind(it) }
}
