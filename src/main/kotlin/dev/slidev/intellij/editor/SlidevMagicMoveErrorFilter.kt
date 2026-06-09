package dev.slidev.intellij.editor

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiErrorElement

/**
 * Suppresses parse-error highlights inside the per-step injections of magic-move blocks
 * (plan.md, 19.5). The steps are intentionally incomplete code mid-animation (a step may
 * end on `const a = {` for the next one to finish), so syntax errors are noise by
 * construction — the [SlidevScriptErrorFilter] rationale. The markdown plugin's own
 * `CodeFenceHighlightInfoFilter` does cover these fragments (their host *is* a markdown
 * code fence), but it is gated on the "show problems in code fences" setting whose
 * default is to show — magic-move steps must stay quiet regardless.
 */
internal class SlidevMagicMoveErrorFilter : HighlightErrorFilter() {

    override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean {
        val file = element.containingFile ?: return true
        val manager = InjectedLanguageManager.getInstance(file.project)
        if (!manager.isInjectedFragment(file)) return true
        return !SlidevMagicMoveSupport.isMagicMoveHost(manager.getInjectionHost(file))
    }
}
