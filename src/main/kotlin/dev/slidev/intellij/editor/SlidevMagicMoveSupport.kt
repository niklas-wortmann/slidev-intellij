package dev.slidev.intellij.editor

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.slidev.intellij.parser.MagicMoveBlock
import dev.slidev.intellij.parser.findMagicMoveBlocks
import dev.slidev.intellij.project.SlidevProjectService
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence

/**
 * Shared guards for the Shiki Magic Move support (plan.md, Phase 19) — the
 * [SlidevScriptSupport] pattern, but for the *Markdown* PSI root the code fences live in
 * (Phase 18 targets the HTML template-data root instead). Used by both
 * [SlidevMagicMoveInjector] and [SlidevMagicMoveErrorFilter]; references markdown-plugin
 * classes, so everything here stays behind slidev-markdown.xml.
 */
internal object SlidevMagicMoveSupport {

    /**
     * The magic-move block spanning [host], or null when the fence is not one.
     * Guards ordered cheapest first — every code fence in every markdown file passes
     * through the injector on each PSI change.
     */
    fun magicMoveBlockOf(host: MarkdownCodeFence): MagicMoveBlock? {
        if (!host.isValidHost) return null
        val text = host.text
        if (!text.startsWith("````") || !text.contains("magic-move")) return null
        val file = host.containingFile ?: return null
        if (!isSlidevDeckMarkdownRoot(file)) return null
        return findMagicMoveBlocks(text).firstOrNull { it.start == 0 }
    }

    /**
     * Whether [file] is the Markdown PSI root of a Slidev deck — same guard family as
     * [SlidevScriptSupport.isSlidevSlideHtmlRoot], but for the root the code fences live
     * in. Also excludes fences inside *injected* markdown fragments: their virtual file
     * is a window into the host document and never matches a registered entry.
     */
    fun isSlidevDeckMarkdownRoot(file: PsiFile): Boolean {
        if (file.language.id != "Markdown") return false
        // Through originalFile: completion re-runs injection on a copy of the file (dummy
        // identifier inserted) whose light virtual file has no registered path — declining
        // there would swap the copy's injection to the markdown fallback and derail the
        // completion machinery's coordinate mapping between original and copy.
        val virtualFile = file.originalFile.viewProvider.virtualFile
        if (!virtualFile.name.endsWith(".md", ignoreCase = true)) return false
        return SlidevProjectService.getInstance(file.project).stateContaining(virtualFile.path) != null
    }

    /** Whether [host] is the outer fence of a magic-move block in a Slidev deck. */
    fun isMagicMoveHost(host: PsiElement?): Boolean =
        host is MarkdownCodeFence && magicMoveBlockOf(host) != null
}
