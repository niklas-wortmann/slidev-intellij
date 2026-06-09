package dev.slidev.intellij.startup

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.slidev.intellij.editor.SlidevEditorDecorations
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.server.SlidevServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kicks off project detection and listener wiring once the project is open. */
internal class SlidevStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Plain getService: serviceAsync is still @Internal/@Experimental (flagged by the
        // Plugin Verifier), and we are already off the EDT here.
        SlidevProjectService.getInstance(project).initialize()
        SlidevServerManager.getInstance(project).startWatcher()
        val decorations = project.getService(SlidevEditorDecorations::class.java)
        withContext(Dispatchers.EDT) {
            decorations.initialize()
        }
    }
}
