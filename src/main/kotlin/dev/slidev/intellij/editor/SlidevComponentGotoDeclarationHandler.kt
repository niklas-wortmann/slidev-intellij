package dev.slidev.intellij.editor

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import dev.slidev.intellij.components.SlidevComponentIndex
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Go-to-declaration from a component tag in slide content to its defining `.vue` file;
 * built-ins have no source file in the project, so their "declaration" opens the
 * external documentation page (plan.md, 14.4). Offset-based over [SlidevSlideTags]
 * like the other component features.
 */
internal class SlidevComponentGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val file = sourceElement?.containingFile ?: return null
        val location = SlidevComponentCompletionContributor.slideLocation(file, offset) ?: return null
        val token = SlidevSlideTags.tokenAt(location.text, location.path, location.offset)
            as? SlidevSlideTags.Token.Tag ?: return null
        val project = file.project
        val component = SlidevComponentIndex.getInstance(project).componentFor(location.path, token.name) ?: return null

        val vuePath = component.filePath
        if (vuePath != null) {
            val state = SlidevProjectService.getInstance(project).stateContaining(location.path) ?: return null
            // The entry file's file system, so the temp file system used in tests works too.
            val target = state.entryFile.fileSystem.findFileByPath(vuePath) ?: return null
            val psiFile = PsiManager.getInstance(project).findFile(target) ?: return null
            return arrayOf(psiFile)
        }
        val docsUrl = component.docsUrl ?: return null
        return arrayOf(DocsUrlElement(file, component.name, docsUrl))
    }

    /** Synthetic navigation target whose "navigation" opens the component's docs page. */
    internal class DocsUrlElement(
        private val context: PsiFile,
        private val componentName: String,
        val url: String,
    ) : FakePsiElement() {
        override fun getParent(): PsiElement = context
        override fun getName(): String = componentName
        override fun canNavigate(): Boolean = true
        override fun navigate(requestFocus: Boolean) = BrowserUtil.browse(url)
    }
}
