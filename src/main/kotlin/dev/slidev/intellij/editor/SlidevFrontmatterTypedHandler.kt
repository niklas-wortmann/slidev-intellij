package dev.slidev.intellij.editor

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Auto-popups the frontmatter completion while typing letters inside a `---` block.
 * Markdown deliberately has no identifier auto-popup (it would fire on prose), so
 * this opts the frontmatter blocks back in, matching the language-server behavior.
 */
internal class SlidevFrontmatterTypedHandler : TypedHandlerDelegate() {

    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (!charTyped.isLetterOrDigit() && charTyped != '-') {
            return Result.CONTINUE
        }
        val context = SlidevFrontmatterCompletionContributor.frontmatterContext(
            file,
            editor.caretModel.offset,
        )
        if (context != null) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }
        return Result.CONTINUE
    }
}
