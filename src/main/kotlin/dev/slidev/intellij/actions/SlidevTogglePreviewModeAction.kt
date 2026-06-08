package dev.slidev.intellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import dev.slidev.intellij.ui.preview.SlidevPreviewService

/** Switches between slide and overview preview, like `slidev.show-slide/overview-preview`. */
internal class SlidevTogglePreviewModeAction : ToggleAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = e.project != null
    }

    override fun isSelected(e: AnActionEvent): Boolean =
        e.project?.let { SlidevPreviewService.getInstance(it).mode } == SlidevPreviewService.Mode.OVERVIEW

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        // setSelected runs on the EDT; the mode setter reloads the preview wrapper.
        SlidevPreviewService.getInstance(project).mode =
            if (state) SlidevPreviewService.Mode.OVERVIEW else SlidevPreviewService.Mode.SLIDE
    }
}
