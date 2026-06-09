package dev.slidev.intellij.ui.preview

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.project.SlidevProjectService
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * The preview half of the Slidev split editor: wraps a [SlidevPreviewComponent] as a [FileEditor].
 * Selecting the editor makes its deck the active Slidev project so the shared preview state,
 * caret sync, and toolbar actions all follow the file being edited.
 */
internal class SlidevPreviewFileEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val component = SlidevPreviewComponent(project)

    init {
        Disposer.register(this, component)
    }

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent = component

    override fun getName(): String = SlidevBundle.message("editor.preview.name")

    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun selectNotify() {
        // No-op when the file is not a registered entry or is already active.
        SlidevProjectService.getInstance(project).setActive(file.path)
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun dispose() {}
}
