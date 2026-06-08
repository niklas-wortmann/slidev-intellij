package dev.slidev.intellij.actions

import com.intellij.openapi.project.Project
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.project.SlidevProjectState

/** Removes the selected projects-tree entry from the registry, like `slidev.remove-entry`. */
internal class SlidevRemoveEntryAction : SlidevProjectStateAction() {

    override fun perform(project: Project, state: SlidevProjectState) {
        SlidevProjectService.getInstance(project).removeEntry(state.entryPath)
    }
}
