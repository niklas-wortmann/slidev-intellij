package dev.slidev.intellij.editor

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Auto-popups the component completion in slide content: on `<` (tag names), on space
 * and `:`/`@` inside an open tag (attributes), and while typing either name (plan.md,
 * 14.5). Markdown deliberately has no identifier auto-popup, so like
 * [SlidevFrontmatterTypedHandler] this opts the slide tags back in.
 */
internal class SlidevComponentTypedHandler : TypedHandlerDelegate() {

    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (charTyped != '<' && charTyped != ' ' && charTyped != ':' && charTyped != '@' &&
            !charTyped.isLetterOrDigit() && charTyped != '-'
        ) {
            return Result.CONTINUE
        }
        val location = SlidevComponentCompletionContributor.slideLocation(
            file,
            editor.caretModel.offset,
        ) ?: return Result.CONTINUE

        if (charTyped == '<') {
            // The tag is only opened once the char lands; the line just has to be content.
            if (SlidevSlideTags.isSlideContent(location.text, location.path, location.offset)) {
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
            }
            return Result.CONTINUE
        }

        val context = SlidevSlideTags.contextAt(location.text, location.path, location.offset)
            ?: return Result.CONTINUE
        // A space right after `<` would pop the full component list on a plain `a < b`.
        if (charTyped == ' ' && context is SlidevSlideTags.Context.TagName && context.prefix.isEmpty()) {
            return Result.CONTINUE
        }
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.CONTINUE
    }
}
