package dev.slidev.intellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.project.SlidevProjectState

/** Makes the selected projects-tree entry the active one, like `slidev.set-as-active`. */
internal class SlidevSetActiveAction : SlidevProjectStateAction() {

    override fun isEnabledFor(e: AnActionEvent, state: SlidevProjectState): Boolean =
        e.project?.let { SlidevProjectService.getInstance(it).activeEntryPath } != state.entryPath

    override fun perform(project: Project, state: SlidevProjectState) {
        SlidevProjectService.getInstance(project).setActive(state.entryPath)
    }
}
