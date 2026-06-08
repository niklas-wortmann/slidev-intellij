package dev.slidev.intellij.startup

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.slidev.intellij.editor.SlidevEditorDecorations
import dev.slidev.intellij.project.SlidevProjectService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kicks off project detection and listener wiring once the project is open. */
internal class SlidevStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.serviceAsync<SlidevProjectService>().initialize()
        val decorations = project.serviceAsync<SlidevEditorDecorations>()
        withContext(Dispatchers.EDT) {
            decorations.initialize()
        }
    }
}
