package dev.slidev.intellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.settings.SlidevSettings

/** Toggles editor &harr; preview cursor sync, like `slidev.enable/disable-preview-sync`. */
internal class SlidevToggleSyncAction : ToggleAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = e.project != null
    }

    override fun isSelected(e: AnActionEvent): Boolean =
        e.project?.let { SlidevSettings.getInstance(it).state.previewSync } == true

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        SlidevSettings.getInstance(project).state.previewSync = state
        SlidevProjectService.getInstance(project).onSettingsChanged(globsChanged = false)
    }
}
