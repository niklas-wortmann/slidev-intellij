package dev.slidev.intellij.ui

import com.intellij.ide.FileIconProvider
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.slidev.intellij.SlidevIcons
import dev.slidev.intellij.project.SlidevListener
import dev.slidev.intellij.project.SlidevProjectService
import javax.swing.Icon

/**
 * Shows the Slidev logo on markdown files registered as Slidev entries,
 * i.e. files matching the include/exclude globs or added manually.
 */
internal class SlidevFileIconProvider : FileIconProvider, DumbAware {

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (project == null || project.isDisposed || !file.name.endsWith(".md", ignoreCase = true)) {
            return null
        }
        return SlidevIcons.File.takeIf { SlidevProjectService.getInstance(project).stateFor(file.path) != null }
    }
}

/** Repaints the project view when the entry registry changes, so file icons stay current. */
internal class SlidevFileIconRefresher(private val project: Project) : SlidevListener {

    override fun projectsChanged() {
        ProjectView.getInstance(project).refresh()
    }
}
