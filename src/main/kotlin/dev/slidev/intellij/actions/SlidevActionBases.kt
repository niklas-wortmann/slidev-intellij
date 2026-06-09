package dev.slidev.intellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import dev.slidev.intellij.parser.SlidevMarkdown
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.project.SlidevProjectState
import dev.slidev.intellij.ui.SlidevDataKeys
import dev.slidev.intellij.ui.preview.SlidevPreviewService

/**
 * A preview toolbar action: enabled while the active project's dev server is running.
 * `update()` runs on a background thread and only touches thread-safe service state;
 * [perform] runs on the EDT and may reach the JCEF panel through the service.
 */
internal abstract class SlidevPreviewAction : AnAction(), DumbAware {

    final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val state = project?.let { SlidevProjectService.getInstance(it).activeState() }
        // Old servers without navState broadcasting can't be navigated, mirroring
        // the `!slidev:preview:compat` when-clauses of the VS Code extension.
        val compatHidden = hiddenInCompatMode && state?.compatMode == true
        e.presentation.isVisible = !compatHidden
        e.presentation.isEnabled = project != null && !compatHidden &&
            state?.serverRunning == true &&
            extraEnabled(SlidevPreviewService.getInstance(project))
    }

    /** Whether the action is hidden while the server runs in compat mode (no version meta). */
    protected open val hiddenInCompatMode: Boolean = false

    protected open fun extraEnabled(service: SlidevPreviewService): Boolean = true

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        perform(SlidevPreviewService.getInstance(project))
    }

    protected abstract fun perform(service: SlidevPreviewService)
}

/**
 * An editor action moving the caret between slides of the current markdown file,
 * the counterpart of the `slidev.prev`/`slidev.next` commands. Only visible in
 * markdown files that belong to a registered Slidev project.
 */
internal abstract class SlidevEditorSlideAction : AnAction(), DumbAware {

    final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        // No caret access here: update() runs on a background thread.
        e.presentation.isEnabledAndVisible = project != null &&
            e.getData(CommonDataKeys.EDITOR) != null &&
            file != null && file.name.endsWith(".md", ignoreCase = true) &&
            SlidevProjectService.getInstance(project).markdownFor(file.path) != null
    }

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val md = SlidevProjectService.getInstance(project).markdownFor(file.path) ?: return
        val target = targetLine(md, editor.caretModel.logicalPosition.line) ?: return
        val line = target.coerceIn(0, (editor.document.lineCount - 1).coerceAtLeast(0))
        editor.caretModel.removeSecondaryCarets()
        editor.caretModel.moveToLogicalPosition(LogicalPosition(line, 0))
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    protected abstract fun targetLine(md: SlidevMarkdown, line: Int): Int?
}

/**
 * An action on the [SlidevProjectState] exposed through the data context,
 * i.e. the selection of the projects tree.
 */
internal abstract class SlidevProjectStateAction : AnAction(), DumbAware {

    final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val state = e.getData(SlidevDataKeys.PROJECT_STATE)
        e.presentation.isEnabled = e.project != null && state != null && isEnabledFor(e, state)
    }

    protected open fun isEnabledFor(e: AnActionEvent, state: SlidevProjectState): Boolean = true

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val state = e.getData(SlidevDataKeys.PROJECT_STATE) ?: return
        perform(project, state)
    }

    protected abstract fun perform(project: Project, state: SlidevProjectState)
}
