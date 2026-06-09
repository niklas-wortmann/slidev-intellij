package dev.slidev.intellij.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.ui.preview.SlidevPreviewComponent
import javax.swing.JComponent

/**
 * Creates the Slidev tool window with the Preview, Slides, and Projects tabs,
 * the counterparts of the three views in the VS Code activity bar container.
 */
internal class SlidevToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // The preview component builds its own toolbar and registers itself on SlidevPreviewService
        // (alongside any split-editor previews), disposed with its content.
        addContent(toolWindow, SlidevPreviewComponent(project), SlidevBundle.message("toolwindow.tab.preview"))
        addContent(toolWindow, SlidevSlidesPanel(project), SlidevBundle.message("toolwindow.tab.slides"))
        addContent(toolWindow, SlidevProjectsPanel(project), SlidevBundle.message("toolwindow.tab.projects"))
    }

    private fun <T> addContent(toolWindow: ToolWindow, panel: T, title: String)
        where T : JComponent, T : com.intellij.openapi.Disposable {
        val content = ContentFactory.getInstance().createContent(panel, title, false)
        content.setDisposer(panel)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
    }
}
