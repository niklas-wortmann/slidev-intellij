package dev.slidev.intellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.guessProjectDir
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.project.SlidevProjectService

/** Registers chosen markdown files as Slidev entries, like `slidev.add-entry`. */
internal class SlidevAddEntryAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
            .withFileFilter { it.name.endsWith(".md", ignoreCase = true) }
            .withTitle(SlidevBundle.message("action.Slidev.AddEntry.text"))
        val service = SlidevProjectService.getInstance(project)
        FileChooser.chooseFiles(descriptor, project, project.guessProjectDir())
            .forEach(service::addEntry)
    }
}
