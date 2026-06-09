package dev.slidev.intellij.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiFile
import dev.slidev.intellij.SlidevIcons
import dev.slidev.intellij.components.SlidevComponent
import dev.slidev.intellij.components.SlidevComponentIndex
import dev.slidev.intellij.components.SlidevComponentProp
import dev.slidev.intellij.project.SlidevProjectService

/** A (host text, file path, host offset) caret location inside a Slidev deck's markdown. */
internal class SlidevSlideLocation(val text: String, val path: String, val offset: Int)

/** Lookup object of an attribute item, carrying what the documentation panel needs. */
internal class SlidevPropLookup(val component: SlidevComponent, val prop: SlidevComponentProp)

/**
 * Completion for Vue component tags and their attributes in slide content (plan.md,
 * 14.1/14.2): `<` offers the component index (built-ins, theme/addon packages, local
 * `components/`), and inside an open tag the component's props — plain and `:`-bound —
 * plus the global Slidev directives. The bundled Markdown plugin offers nothing for
 * inline HTML tags (verified against 2025.3: its only contributors cover image tags
 * and the completion dummy identifier), and the Vue plugin only exists in paid IDEs,
 * so this is text-offset-based over [SlidevSlideTags], like the frontmatter support.
 */
internal class SlidevComponentCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        val location = slideLocation(file, parameters.offset) ?: return
        val context = SlidevSlideTags.contextAt(location.text, location.path, location.offset) ?: return
        val index = SlidevComponentIndex.getInstance(file.project)

        when (context) {
            is SlidevSlideTags.Context.TagName -> {
                val matcher = result.withPrefixMatcher(context.prefix)
                for (component in index.componentsFor(location.path).values) {
                    var element = LookupElementBuilder.create(component, component.name)
                        .withIcon(SlidevIcons.ToolWindow)
                        .withTypeText(component.origin.name.lowercase(), true)
                        .withTailText(component.description?.lineSequence()?.firstOrNull()?.let { "  $it" }, true)
                    if (!context.closing) {
                        element = element.withInsertHandler { insertion, _ -> insertSelfClosingTail(insertion) }
                    }
                    matcher.addElement(element)
                }
            }

            is SlidevSlideTags.Context.AttributeName -> {
                val matcher = result.withPrefixMatcher(context.prefix)
                for (directive in index.directives()) {
                    matcher.addElement(
                        LookupElementBuilder.create(directive, directive.name)
                            .withIcon(SlidevIcons.ToolWindow)
                            .withTypeText("directive", true)
                            .withTailText(directive.description?.lineSequence()?.firstOrNull()?.let { "  $it" }, true),
                    )
                }
                val component = index.componentFor(location.path, context.tagName) ?: return
                for (prop in component.props) {
                    val boolean = prop.type?.contains("boolean", ignoreCase = true) == true
                    var plain = LookupElementBuilder.create(SlidevPropLookup(component, prop), prop.name)
                        .withIcon(SlidevIcons.ToolWindow)
                        .withTypeText(prop.typeText, true)
                        .withTailText(prop.description?.lineSequence()?.firstOrNull()?.let { "  $it" }, true)
                    if (!boolean) {
                        plain = plain.withInsertHandler { insertion, _ -> insertAttributeValueQuotes(insertion) }
                    }
                    matcher.addElement(plain)
                    matcher.addElement(
                        // `:`-bound variant; the value is an expression, so always quoted.
                        LookupElementBuilder.create(SlidevPropLookup(component, prop), ":${prop.name}")
                            .withIcon(SlidevIcons.ToolWindow)
                            .withTypeText(prop.typeText, true)
                            .withInsertHandler { insertion, _ -> insertAttributeValueQuotes(insertion) },
                    )
                }
            }
        }
    }

    companion object {

        /**
         * The slide-content caret location, or null when [file] is not part of a Slidev
         * project. Injected offsets (the headmatter YAML, code fences) are mapped back to
         * the host markdown file so they hit the frontmatter/fence exclusion ranges.
         */
        internal fun slideLocation(file: PsiFile, offset: Int): SlidevSlideLocation? {
            val (hostFile, document, hostOffset) =
                SlidevFrontmatterCompletionContributor.hostLocation(file, offset) ?: return null
            val virtualFile = hostFile.viewProvider.virtualFile
            if (!virtualFile.name.endsWith(".md", ignoreCase = true)) {
                return null
            }
            SlidevProjectService.getInstance(hostFile.project).stateContaining(virtualFile.path) ?: return null
            return SlidevSlideLocation(document.charsSequence.toString(), virtualFile.path, hostOffset)
        }

        /** Completes `<Tweet` to `<Tweet />` with the caret before the `/>`. */
        private fun insertSelfClosingTail(insertion: InsertionContext) {
            val text = insertion.document.charsSequence
            val next = if (insertion.tailOffset < text.length) text[insertion.tailOffset] else null
            if (next != '>' && next != '/' && next != ' ') {
                insertion.document.insertString(insertion.tailOffset, " />")
                insertion.editor.caretModel.moveToOffset(insertion.tailOffset + 1)
            }
        }

        /** Completes an attribute to `name="<caret>"`. */
        private fun insertAttributeValueQuotes(insertion: InsertionContext) {
            val text = insertion.document.charsSequence
            if (insertion.tailOffset >= text.length || text[insertion.tailOffset] != '=') {
                insertion.document.insertString(insertion.tailOffset, "=\"\"")
                insertion.editor.caretModel.moveToOffset(insertion.tailOffset + 2)
            }
        }
    }
}
