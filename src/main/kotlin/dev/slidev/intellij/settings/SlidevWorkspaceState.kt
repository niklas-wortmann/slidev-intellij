package dev.slidev.intellij.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Per-user workspace state: the registered Slidev entry files and the active one,
 * the counterpart of the VS Code extension's workspace-state keys.
 * Paths are stored slash-normalized and absolute.
 */
@Service(Service.Level.PROJECT)
@State(name = "SlidevWorkspace", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class SlidevWorkspaceState : PersistentStateComponent<SlidevWorkspaceState.State> {

    class State {
        var entries: MutableList<String> = mutableListOf()
        var activeEntry: String? = null

        /** Last used preview mode: "slide" or "overview". */
        var previewMode: String = "slide"
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): SlidevWorkspaceState = project.service()
    }
}
