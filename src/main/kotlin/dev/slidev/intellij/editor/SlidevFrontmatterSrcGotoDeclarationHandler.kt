package dev.slidev.intellij.editor

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import dev.slidev.intellij.parser.SlidevDataLoader
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Go-to-declaration from a frontmatter `src:` import value to the imported markdown
 * file (https://sli.dev/features/importing-slides). Offset-based like the other
 * frontmatter features because mid-document blocks are plain Markdown paragraphs;
 * the headmatter's injected YAML offsets are mapped back to the host file.
 */
internal class SlidevFrontmatterSrcGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val file = sourceElement?.containingFile ?: return null
        val (hostFile, document, hostOffset) =
            SlidevFrontmatterCompletionContributor.hostLocation(file, offset) ?: return null
        val virtualFile = hostFile.viewProvider.virtualFile
        if (!virtualFile.name.endsWith(".md", ignoreCase = true)) {
            return null
        }
        val state = SlidevProjectService.getInstance(hostFile.project).stateContaining(virtualFile.path) ?: return null

        val text = document.charsSequence.toString()
        val blocks = SlidevFrontmatterBlocks.blocks(text, virtualFile.path)
        val line = document.getLineNumber(hostOffset.coerceIn(0, text.length))
        val src = SlidevFrontmatterBlocks.srcValueAt(text.lines(), blocks, line) ?: return null
        if (hostOffset - document.getLineStartOffset(line) !in src.columns) {
            return null
        }

        val path = SlidevDataLoader.resolveSrcPath(src.value, virtualFile.path, state.root)
        // The entry file's file system, so the temp file system used in tests works too.
        val target = state.entryFile.fileSystem.findFileByPath(path) ?: return null
        val psiFile = PsiManager.getInstance(hostFile.project).findFile(target) ?: return null
        return arrayOf(psiFile)
    }
}
