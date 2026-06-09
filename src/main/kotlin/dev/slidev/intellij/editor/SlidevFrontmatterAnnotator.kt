package dev.slidev.intellij.editor

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.schema.SlidevSchemas
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.MarkedYAMLException

/**
 * Schema validation of frontmatter blocks, the counterpart of the language server's
 * YAML-schema diagnostics: flags YAML syntax errors, non-mapping frontmatter, and
 * values that match none of the schema-allowed types or enum constants. Unknown keys
 * stay legal — the Slidev schemas allow additional properties.
 */
internal class SlidevFrontmatterAnnotator : Annotator {

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
        val lines = text.lines()
        for (block in SlidevFrontmatterBlocks.blocks(text, file.path)) {
            annotateBlock(block, lines, document, holder)
        }
    }

    private fun annotateBlock(
        block: SlidevFrontmatterBlocks.Block,
        lines: List<String>,
        document: Document,
        holder: AnnotationHolder,
    ) {
        if (block.contentLines.isEmpty()) {
            return
        }
        val yaml = block.contentLines.joinToString("\n") { lines[it] }
        if (yaml.isBlank()) {
            return
        }

        val loaded = try {
            Yaml(SafeConstructor(LoaderOptions())).load<Any?>(yaml)
        }
        catch (e: MarkedYAMLException) {
            val line = (e.problemMark?.line ?: 0) + block.contentLines.first
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                SlidevBundle.message("frontmatter.yaml.error", e.problem ?: e.message.orEmpty()),
            ).range(lineRange(document, line)).create()
            return
        }
        catch (e: Exception) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                SlidevBundle.message("frontmatter.yaml.error", e.message.orEmpty()),
            ).range(blockRange(document, block)).create()
            return
        }

        if (loaded !is Map<*, *>) {
            holder.newAnnotation(HighlightSeverity.ERROR, SlidevBundle.message("frontmatter.not.mapping"))
                .range(blockRange(document, block))
                .create()
            return
        }

        val schema = SlidevSchemas.forSlide(block.slideIndex)
        for ((key, value) in loaded) {
            val property = schema.properties[key?.toString()] ?: continue
            if (property.matches(value)) {
                continue
            }
            val line = SlidevFrontmatterBlocks.keyLine(lines, block, property.name) ?: block.contentLines.first
            holder.newAnnotation(
                HighlightSeverity.WARNING,
                SlidevBundle.message("frontmatter.invalid.value", property.name, property.expectation),
            ).range(lineRange(document, line)).create()
        }
    }

    private fun lineRange(document: Document, line: Int): TextRange {
        val clamped = line.coerceIn(0, (document.lineCount - 1).coerceAtLeast(0))
        return TextRange(document.getLineStartOffset(clamped), document.getLineEndOffset(clamped))
    }

    private fun blockRange(document: Document, block: SlidevFrontmatterBlocks.Block): TextRange =
        TextRange(
            lineRange(document, block.contentLines.first).startOffset,
            lineRange(document, block.contentLines.last).endOffset,
        )
}
