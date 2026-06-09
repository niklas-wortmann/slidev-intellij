package dev.slidev.intellij.editor

import com.intellij.lang.Language
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import dev.slidev.intellij.schema.SchemaProperty
import dev.slidev.intellij.schema.SlidevSchemas

/**
 * Quick documentation for the completion popup: the lookup-item bridge is the only legacy
 * documentation path still hit on current platforms. Hover/caret documentation is served by
 * [SlidevFrontmatterDocumentationTargetProvider] (the V2 API), which is consulted first.
 */
internal class SlidevFrontmatterDocumentationProvider : AbstractDocumentationProvider() {

    override fun getDocumentationElementForLookupItem(
        psiManager: PsiManager,
        obj: Any?,
        element: PsiElement?,
    ): PsiElement? {
        // The completion contributor uses the schema property as the lookup object; the
        // context element is unusable here (the platform falls back to the whole file).
        val property = obj as? SchemaProperty ?: return null
        return PropertyDocElement(element ?: return null, property)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val property = (element as? PropertyDocElement)?.property
            ?: (originalElement as? PropertyDocElement)?.property
            ?: return null
        return SlidevFrontmatterDocs.html(property)
    }

    /** Synthetic documentation target for a lookup item, carrying its schema property. */
    private class PropertyDocElement(
        private val context: PsiElement,
        val property: SchemaProperty,
    ) : FakePsiElement() {
        override fun getParent(): PsiElement = context
        override fun getName(): String = property.name
        override fun getLanguage(): Language = context.language
    }
}

/**
 * Schema-property lookup and documentation HTML shared by the hover and lookup-item
 * documentation entry points, rendering the schema's `markdownDescription` like the
 * language-server hovers in VS Code.
 */
internal object SlidevFrontmatterDocs {

    /** The schema property for the frontmatter key at ([file], [offset]), or null. */
    fun propertyAt(file: PsiFile, offset: Int): SchemaProperty? {
        // Hover in the headmatter resolves inside the injected YAML fragment; map back
        // to the host markdown file so line lookups use host coordinates.
        val (_, document, hostOffset) = SlidevFrontmatterCompletionContributor.hostLocation(file, offset)
            ?: return null
        val context = SlidevFrontmatterCompletionContributor.frontmatterContext(file, offset)
            ?: return null
        val key = when (context) {
            is SlidevFrontmatterBlocks.Context.Key -> {
                // Hovering anywhere on the key: take the full key of the line, not the caret prefix.
                val line = document.getLineNumber(hostOffset)
                val text = document.charsSequence.subSequence(
                    document.getLineStartOffset(line),
                    document.getLineEndOffset(line),
                ).toString()
                text.substringBefore(':').trim()
            }
            is SlidevFrontmatterBlocks.Context.Value -> context.key
        }
        return SlidevSchemas.forSlide(context.block.slideIndex).properties[key]
    }

    /** The documentation HTML for [property], or null when the schema has no description. */
    fun html(property: SchemaProperty): String? {
        val description = property.markdownDescription ?: property.description ?: return null
        return buildString {
            append("<b>").append(StringUtil.escapeXmlEntities(property.name)).append("</b>")
            append(" <code>").append(StringUtil.escapeXmlEntities(property.typeText)).append("</code>")
            append("<br/><br/>")
            append(renderMarkdown(description))
        }
    }

    /**
     * Just enough markdown for the schema texts: paragraphs, bullet lists, inline code,
     * bare links. Also used for the component/directive descriptions of [SlidevComponentDocs].
     */
    internal fun renderMarkdown(text: String): String {
        val escaped = StringUtil.escapeXmlEntities(text)
            .replace(Regex("`([^`]+)`"), "<code>$1</code>")
            .replace(Regex("(https?://\\S+?)([.,)]?)(\\s|$)"), "<a href=\"$1\">$1</a>$2$3")
        return escaped.split("\n\n").joinToString("") { paragraph ->
            val lines = paragraph.lines()
            if (lines.all { it.startsWith("- ") || it.isBlank() }) {
                "<ul>" + lines.filter { it.isNotBlank() }.joinToString("") { "<li>${it.removePrefix("- ")}</li>" } + "</ul>"
            }
            else {
                "<p>${paragraph.replace("\n", "<br/>")}</p>"
            }
        }
    }
}
