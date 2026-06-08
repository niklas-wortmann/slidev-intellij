package dev.slidev.intellij.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Shareable per-project settings, the counterparts of the `slidev.*` settings
 * of the VS Code extension.
 */
@Service(Service.Level.PROJECT)
@State(name = "SlidevSettings", storages = [Storage("slidev.xml")])
class SlidevSettings : PersistentStateComponent<SlidevSettings.State> {

    class State {
        var port: Int = DEFAULT_PORT
        var annotations: Boolean = true
        var annotationsLineNumbers: Boolean = true
        var previewSync: Boolean = true
        var include: MutableList<String> = mutableListOf("**/slides.md")
        var exclude: String = "**/node_modules/**"
        var devCommand: String = DEFAULT_DEV_COMMAND
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        const val DEFAULT_PORT = 3030
        const val DEFAULT_DEV_COMMAND = "npm exec -c 'slidev \${args}'"

        @JvmStatic
        fun getInstance(project: Project): SlidevSettings = project.service()
    }
}
