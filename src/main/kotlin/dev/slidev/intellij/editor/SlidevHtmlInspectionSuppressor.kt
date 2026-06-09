package dev.slidev.intellij.editor

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import dev.slidev.intellij.components.SlidevComponentIndex
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Suppresses the platform's unknown-tag/attribute warnings on the HTML template-data
 * root of Slidev decks (and the injected ```html fences inside them), where they are
 * false positives by design:
 *  - component tags resolved by the index — including kebab-case usage (`<v-click>`
 *    → `VClick`) — are not "unknown HTML tags";
 *  - Slidev/Vue directives (`v-click.fade`, `v-mark.circle.orange`) and UnoCSS
 *    attributify utilities (`mt-12`, enabled by default in Slidev) make practically
 *    any attribute name legitimate, so unknown-attribute warnings are suppressed
 *    wholesale, as is the Vue plugin's unrecognized-directive inspection (Slidev's
 *    directives are registered at runtime, invisible to static analysis).
 * Registered for XML so it covers elements reporting either the XML or HTML language;
 * [SlidevComponentAnnotator] keeps the typo-catching for unknown PascalCase tags.
 */
internal class SlidevHtmlInspectionSuppressor : InspectionSuppressor {

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean = when (toolId) {
        "HtmlUnknownAttribute", "HtmlUnknownBooleanAttribute", "VueUnrecognizedDirective" ->
            slidevDeckPath(element.containingFile) != null

        "HtmlUnknownTag" -> {
            val path = slidevDeckPath(element.containingFile)
            val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false)
            path != null && tag != null &&
                SlidevComponentIndex.getInstance(element.project).componentFor(path, tag.name) != null
        }

        else -> false
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
        SuppressQuickFix.EMPTY_ARRAY

    /**
     * The deck's markdown path when [file] is HTML belonging to a Slidev deck: the
     * markdown file's HTML template-data root, or an HTML fragment injected into one
     * of its fenced code blocks.
     */
    private fun slidevDeckPath(file: PsiFile?): String? {
        if (file == null || file.language !== HTMLLanguage.INSTANCE) {
            return null
        }
        if (SlidevScriptSupport.isSlidevSlideHtmlRoot(file)) {
            return file.viewProvider.virtualFile.path
        }
        val host = InjectedLanguageManager.getInstance(file.project).getInjectionHost(file) ?: return null
        val hostFile = host.containingFile.viewProvider.virtualFile
        if (!hostFile.name.endsWith(".md", ignoreCase = true)) {
            return null
        }
        return hostFile.path.takeIf {
            SlidevProjectService.getInstance(file.project).stateContaining(it) != null
        }
    }
}
