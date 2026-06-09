package dev.slidev.intellij.editor

import com.intellij.lang.Language
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.model.Pointer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import dev.slidev.intellij.SlidevIcons
import dev.slidev.intellij.components.SlidevComponent
import dev.slidev.intellij.components.SlidevComponentIndex
import dev.slidev.intellij.components.SlidevComponentProp
import dev.slidev.intellij.components.SlidevDirective

/**
 * Hover/quick documentation for component tags, their props, and the global Slidev
 * directives in slide content (plan.md, 14.3), via the V2 documentation API — like
 * [SlidevFrontmatterDocumentationTargetProvider], the legacy provider path would be
 * shadowed before ever being consulted.
 */
internal class SlidevComponentDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val location = SlidevComponentCompletionContributor.slideLocation(file, offset) ?: return emptyList()
        val token = SlidevSlideTags.tokenAt(location.text, location.path, location.offset) ?: return emptyList()
        val index = SlidevComponentIndex.getInstance(file.project)

        val (name, html) = when (token) {
            is SlidevSlideTags.Token.Tag -> {
                val component = index.componentFor(location.path, token.name) ?: return emptyList()
                component.name to SlidevComponentDocs.componentHtml(component)
            }

            is SlidevSlideTags.Token.Attribute -> {
                val directive = index.directive(token.name.substringBefore('.'))
                if (directive != null) {
                    directive.name to SlidevComponentDocs.directiveHtml(directive)
                }
                else {
                    val component = index.componentFor(location.path, token.tagName) ?: return emptyList()
                    val prop = component.prop(SlidevComponentDocs.propName(token.name)) ?: return emptyList()
                    prop.name to SlidevComponentDocs.propHtml(component, prop)
                }
            }
        }
        return listOf(ComponentTarget(name, html))
    }

    /** Fully precomputed and immutable, so the target can serve as its own pointer. */
    private class ComponentTarget(
        private val name: String,
        private val html: String,
    ) : DocumentationTarget {

        override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

        override fun computePresentation(): TargetPresentation =
            TargetPresentation.builder(name).icon(SlidevIcons.ToolWindow).presentation()

        override fun computeDocumentation(): DocumentationResult = DocumentationResult.documentation(html)

        override fun computeDocumentationHint(): String = html
    }
}

/**
 * Quick documentation for the completion popup; like the frontmatter counterpart, the
 * lookup-item bridge is the only legacy documentation path still hit on current platforms.
 */
internal class SlidevComponentDocumentationProvider : AbstractDocumentationProvider() {

    override fun getDocumentationElementForLookupItem(
        psiManager: PsiManager,
        obj: Any?,
        element: PsiElement?,
    ): PsiElement? {
        val html = when (obj) {
            is SlidevComponent -> SlidevComponentDocs.componentHtml(obj)
            is SlidevPropLookup -> SlidevComponentDocs.propHtml(obj.component, obj.prop)
            is SlidevDirective -> SlidevComponentDocs.directiveHtml(obj)
            else -> return null
        }
        return ComponentDocElement(element ?: return null, html)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? =
        (element as? ComponentDocElement)?.html ?: (originalElement as? ComponentDocElement)?.html

    /** Synthetic documentation target for a lookup item, carrying its rendered docs. */
    private class ComponentDocElement(
        private val context: PsiElement,
        val html: String,
    ) : FakePsiElement() {
        override fun getParent(): PsiElement = context
        override fun getLanguage(): Language = context.language
    }
}

/** Documentation HTML for components, props, and directives of the component index. */
internal object SlidevComponentDocs {

    /** Strips the Vue binding sigils of an attribute name: `:id`/`v-bind:id` → `id`. */
    fun propName(attributeName: String): String =
        attributeName.removePrefix("v-bind").removePrefix(":").removePrefix("@")

    fun componentHtml(component: SlidevComponent): String = buildString {
        append("<b>").append(StringUtil.escapeXmlEntities(component.name)).append("</b>")
        append(" <code>").append(originLabel(component)).append("</code>")
        component.description?.let {
            append("<br/><br/>").append(SlidevFrontmatterDocs.renderMarkdown(it))
        }
        if (component.props.isNotEmpty()) {
            append("<br/>Props:<ul>")
            for (prop in component.props) {
                append("<li><code>").append(StringUtil.escapeXmlEntities(prop.name)).append("</code>")
                append(" <code>").append(StringUtil.escapeXmlEntities(prop.typeText)).append("</code>")
                prop.description?.let { append(" — ").append(StringUtil.escapeXmlEntities(it)) }
                append("</li>")
            }
            append("</ul>")
        }
        appendDocsLink(component.docsUrl)
    }

    fun propHtml(component: SlidevComponent, prop: SlidevComponentProp): String = buildString {
        append("<b>").append(StringUtil.escapeXmlEntities(prop.name)).append("</b>")
        append(" <code>").append(StringUtil.escapeXmlEntities(prop.typeText)).append("</code>")
        append(" — prop of <code>").append(StringUtil.escapeXmlEntities(component.name)).append("</code>")
        if (prop.required) {
            append(" (required)")
        }
        prop.default?.let {
            append("<br/>Default: <code>").append(StringUtil.escapeXmlEntities(it)).append("</code>")
        }
        prop.description?.let {
            append("<br/><br/>").append(SlidevFrontmatterDocs.renderMarkdown(it))
        }
        appendDocsLink(component.docsUrl)
    }

    fun directiveHtml(directive: SlidevDirective): String = buildString {
        append("<b>").append(StringUtil.escapeXmlEntities(directive.name)).append("</b>")
        append(" <code>directive</code>")
        directive.description?.let {
            append("<br/><br/>").append(SlidevFrontmatterDocs.renderMarkdown(it))
        }
        appendDocsLink(directive.docsUrl)
    }

    private fun originLabel(component: SlidevComponent): String =
        "${component.origin.name.lowercase()} component"

    private fun StringBuilder.appendDocsLink(url: String?) {
        url?.let { append("<br/><a href=\"").append(it).append("\">").append(it).append("</a>") }
    }
}
