package dev.slidev.intellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.SimpleListCellRenderer
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.project.SlidevProjectState

/** Picks the active entry from the registered ones via a popup, like `slidev.choose-entry`. */
internal class SlidevChooseEntryAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
            SlidevProjectService.getInstance(project).projects().isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        showPopup(project, e)
    }

    companion object {

        /** Shows the entry chooser; reused by the slides-tree empty-state link. */
        fun showPopup(project: Project, e: AnActionEvent? = null) {
            val service = SlidevProjectService.getInstance(project)
            val entries = service.projects()
            if (entries.isEmpty()) {
                return
            }
            val basePath = project.basePath
            val popup = JBPopupFactory.getInstance()
                .createPopupChooserBuilder(entries)
                .setTitle(SlidevBundle.message("choose.entry.title"))
                .setRenderer(SimpleListCellRenderer.create("") { state -> presentableText(state, basePath) })
                .setItemChosenCallback { state -> service.setActive(state.entryPath) }
                .createPopup()
            val dataContext = e?.dataContext
            if (dataContext != null) {
                popup.showInBestPositionFor(dataContext)
            }
            else {
                popup.showCenteredInCurrentWindow(project)
            }
        }

        private fun presentableText(state: SlidevProjectState, basePath: String?): String {
            val relative = basePath?.let { FileUtil.getRelativePath(it, state.entryPath, '/') }
            return relative ?: state.entryPath
        }
    }
}
