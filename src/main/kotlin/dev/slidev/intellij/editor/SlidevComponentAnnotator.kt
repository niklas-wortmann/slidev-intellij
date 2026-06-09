package dev.slidev.intellij.editor

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.components.SlidevComponent
import dev.slidev.intellij.components.SlidevComponentIndex
import dev.slidev.intellij.components.componentForTag
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Semantic coloring of component tags and Vue attributes in slide content (plan.md,
 * 15.1): known component names get [SlidevHighlightColors.COMPONENT_TAG], `v-`/`:`/`@`
 * attributes their directive/binding colors, and an unrecognized PascalCase tag a weak
 * warning (typo-catcher). Layers on top of the Markdown plugin's generic HTML-tag
 * coloring; like [SlidevFrontmatterAnnotator] it works on the document text because
 * inline tags are scattered `HTML_TAG` tokens in the Markdown PSI.
 */
internal class SlidevComponentAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Annotate once per file, not per PSI element.
        if (element !is PsiFile) {
            return
        }
        val file = element.viewProvider.virtualFile
        if (!file.name.endsWith(".md", ignoreCase = true)) {
            return
        }
        val document = element.viewProvider.document ?: return
        SlidevProjectService.getInstance(element.project).stateContaining(file.path) ?: return

        val text = document.charsSequence.toString()
        val components = SlidevComponentIndex.getInstance(element.project).componentsFor(file.path)
        for (token in SlidevSlideTags.tokens(text, file.path)) {
            when (token) {
                is SlidevSlideTags.Token.Tag -> annotateTag(token, components, holder)
                is SlidevSlideTags.Token.Attribute -> annotateAttribute(token, holder)
            }
        }
    }

    private fun annotateTag(
        token: SlidevSlideTags.Token.Tag,
        components: Map<String, SlidevComponent>,
        holder: AnnotationHolder,
    ) {
        when {
            // Exact or kebab-case match (`<v-click>` → VClick), like Vue's resolution.
            components.componentForTag(token.name) != null ->
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(token.range.asTextRange)
                    .textAttributes(SlidevHighlightColors.COMPONENT_TAG)
                    .create()

            // Vue components are PascalCase; lowercase tags are plain HTML. Closing
            // tags are skipped so a typo is reported once, on the opening tag.
            !token.closing && token.name.first().isUpperCase() ->
                holder.newAnnotation(
                    HighlightSeverity.WEAK_WARNING,
                    SlidevBundle.message("component.unknown", token.name),
                ).range(token.range.asTextRange).create()
        }
    }

    private fun annotateAttribute(token: SlidevSlideTags.Token.Attribute, holder: AnnotationHolder) {
        val key = when {
            token.name.startsWith("v-") -> SlidevHighlightColors.DIRECTIVE_ATTRIBUTE
            token.name.startsWith(":") || token.name.startsWith("@") -> SlidevHighlightColors.BOUND_ATTRIBUTE
            else -> return
        }
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(token.range.asTextRange)
            .textAttributes(key)
            .create()
    }

    private val IntRange.asTextRange: TextRange get() = TextRange(first, last + 1)
}
