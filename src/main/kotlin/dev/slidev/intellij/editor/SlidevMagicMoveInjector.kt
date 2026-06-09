package dev.slidev.intellij.editor

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence

/**
 * Per-step language injection into [Shiki Magic Move](https://sli.dev/features/shiki-magic-move)
 * blocks (plan.md, 19.4, Design B). The outer 4-backtick `md magic-move` fence is a
 * [MarkdownCodeFence] — the markdown plugin's one reliable injection host (the 8.4/15.2
 * outcomes) — and each inner 3-backtick step gets its own injection session with the
 * language resolved by [SlidevFenceLanguages] (Shiki meta like `{*|2|5-6}` stripped by
 * the scanner). The 19.1 spike showed why the platform alone can't do this: the guesser
 * resolves the outer info string to Markdown via its space-chopping (`md`), but fences
 * nested in *injected* markdown never get recursive injection, so steps stayed plain.
 *
 * Registered `order="first"` to run before the markdown plugin's `CodeFenceInjector` —
 * injector order is first-wins per host, and the fallback Markdown injection would
 * otherwise shadow the step injections. Deck-guarded; in non-Slidev markdown the
 * platform's Markdown injection keeps applying.
 */
internal class SlidevMagicMoveInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(MarkdownCodeFence::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val host = context as? MarkdownCodeFence ?: return
        val block = SlidevMagicMoveSupport.magicMoveBlockOf(host) ?: return
        for (step in block.steps) {
            if (step.contentEnd <= step.contentStart) continue
            val language = SlidevFenceLanguages.resolve(step.language) ?: continue
            registrar.startInjecting(language)
                .addPlace(null, null, host, TextRange(step.contentStart, step.contentEnd))
                .doneInjecting()
        }
    }
}
