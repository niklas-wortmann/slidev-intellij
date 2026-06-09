package dev.slidev.intellij.editor

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import dev.slidev.intellij.SlidevIcons

/**
 * Hover/quick documentation for schema-known frontmatter keys via the V2 documentation API.
 * Hover targets are resolved as: this EP, then symbol declarations, then the legacy
 * documentation-provider path. A top-level YAML key is a symbol declaration, so hovering a
 * headmatter key would otherwise stop at the bare declaration popup without ever consulting
 * [SlidevFrontmatterDocumentationProvider]; registering here puts the schema docs first.
 */
internal class SlidevFrontmatterDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val property = SlidevFrontmatterDocs.propertyAt(file, offset) ?: return emptyList()
        val html = SlidevFrontmatterDocs.html(property) ?: return emptyList()
        return listOf(PropertyTarget(property.name, html))
    }

    /** Fully precomputed and immutable, so the target can serve as its own pointer. */
    private class PropertyTarget(
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
