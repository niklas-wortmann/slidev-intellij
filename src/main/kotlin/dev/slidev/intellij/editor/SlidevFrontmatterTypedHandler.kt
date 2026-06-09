package dev.slidev.intellij.editor

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Auto-popups the frontmatter completion while typing letters inside a `---` block,
 * plus `.` and `/` inside a `src:` import value to pop the path completion.
 * Markdown deliberately has no identifier auto-popup (it would fire on prose), so
 * this opts the frontmatter blocks back in, matching the language-server behavior.
 */
internal class SlidevFrontmatterTypedHandler : TypedHandlerDelegate() {

    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        val pathChar = charTyped == '.' || charTyped == '/'
        if (!charTyped.isLetterOrDigit() && charTyped != '-' && !pathChar) {
            return Result.CONTINUE
        }
        val context = SlidevFrontmatterCompletionContributor.frontmatterContext(
            file,
            editor.caretModel.offset,
        ) ?: return Result.CONTINUE
        // `.` and `/` only pop the path list of a `src:` import value, not other values.
        if (pathChar && (context !is SlidevFrontmatterBlocks.Context.Value || context.key != "src")) {
            return Result.CONTINUE
        }
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.CONTINUE
    }
}
