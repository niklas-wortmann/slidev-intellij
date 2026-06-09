package dev.slidev.intellij.editor

import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Shared guards for the JS/TS script support in slide content (plan.md, Phase 18).
 * All of it targets the markdown file's HTML template-data root: the Markdown root has
 * no injection hosts for inline HTML (the 8.4/15.2 spike outcomes), but inline HTML
 * blocks are fully re-parsed by the real HTML parser in the HTML root. There the
 * JavaScript plugin's embedded-content support already turns `<script>` bodies into
 * JS PSI (the 18.1 spike outcome, "Path B"), and `XmlAttributeValue` is a standard
 * injection host for Vue expression attributes. Free of JS-plugin classes — languages
 * are looked up by stable ID strings — so it is also safe to reference from classes
 * registered in slidev-markdown.xml (e.g. [SlidevHtmlInspectionSuppressor]).
 */
internal object SlidevScriptSupport {

    /**
     * Whether [file] is the HTML template-data root of a Slidev deck's markdown file.
     * Guards ordered cheapest first — these are consulted for every XML element in
     * every XML-flavored file on each PSI change.
     */
    fun isSlidevSlideHtmlRoot(file: PsiFile): Boolean {
        if (file.language !== HTMLLanguage.INSTANCE) return false // HTML root only
        val viewProvider = file.viewProvider
        val virtualFile = viewProvider.virtualFile
        if (!virtualFile.name.endsWith(".md", ignoreCase = true)) return false // markdown files only
        // String-ID comparison, no Markdown-plugin class needed; also excludes injected
        // HTML fragments inside ```html fences (their baseLanguage is HTML, not Markdown).
        if (viewProvider.baseLanguage.id != "Markdown") return false
        return SlidevProjectService.getInstance(file.project).stateContaining(virtualFile.path) != null
    }

    /** Whether [element]'s language is JavaScript or one of its dialects (incl. TypeScript). */
    fun isJavaScriptKind(element: PsiElement): Boolean {
        val js = Language.findLanguageByID("JavaScript") ?: return false
        return element.language.isKindOf(js)
    }
}
