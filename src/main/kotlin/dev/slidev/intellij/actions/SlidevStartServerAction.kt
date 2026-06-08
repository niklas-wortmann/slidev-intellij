package dev.slidev.intellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import dev.slidev.intellij.project.SlidevProjectService

/** Starts the dev server of the active project, like `slidev.start-dev`. */
internal class SlidevStartServerAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val state = e.project?.let { SlidevProjectService.getInstance(it).activeState() }
        // Hidden while running or starting (process spawned but the port not detected yet).
        e.presentation.isEnabledAndVisible =
            state != null && !state.serverRunning && state.processHandler == null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = SlidevProjectService.getInstance(project)
        service.activeState()?.let(service::startServer)
    }
}
