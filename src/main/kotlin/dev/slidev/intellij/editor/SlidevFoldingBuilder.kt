package dev.slidev.intellij.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.parser.FrontmatterStyle
import dev.slidev.intellij.parser.SlidevParser
import dev.slidev.intellij.parser.SourceSlide
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Folds each slide of a Slidev markdown file into one region, the counterpart of
 * `foldings.ts` in the VS Code extension. Only files belonging to a registered Slidev
 * project are folded, so plain markdown with `---` thematic breaks stays untouched.
 * Like the VS Code provider, the document is re-parsed fresh on each request because
 * the service data is debounced and lags behind the editor.
 */
internal class SlidevFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val file = root.containingFile?.virtualFile ?: return FoldingDescriptor.EMPTY_ARRAY
        SlidevProjectService.getInstance(root.project).stateContaining(file.path) ?: return FoldingDescriptor.EMPTY_ARRAY

        val md = SlidevParser.parse(document.charsSequence.toString(), file.path)
        val descriptors = mutableListOf<FoldingDescriptor>()
        for (slide in md.slides) {
            // The region starts at the preceding `---` separator, except when the slide opens
            // with a frontmatter block whose own `---` already is the first line of the slide.
            val startLine = maxOf(0, if (slide.frontmatterStyle == FrontmatterStyle.FRONTMATTER) slide.start else slide.start - 1)
            val endLine = minOf(slide.end - 1, document.lineCount - 1)
            if (endLine <= startLine) {
                continue
            }
            val range = TextRange(document.getLineStartOffset(startLine), document.getLineEndOffset(endLine))
            if (range.isEmpty) {
                continue
            }
            descriptors.add(FoldingDescriptor(root.node, range, null, placeholder(slide)))
        }
        return descriptors.toTypedArray()
    }

    private fun placeholder(slide: SourceSlide): String {
        val no = slide.index + 1
        return slide.title
            ?.let { SlidevBundle.message("folding.slide.titled", no, it) }
            ?: SlidevBundle.message("folding.slide", no)
    }

    override fun getPlaceholderText(node: ASTNode): String = "..."

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
