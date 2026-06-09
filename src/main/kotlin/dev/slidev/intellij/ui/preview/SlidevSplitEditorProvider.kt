package dev.slidev.intellij.ui.preview

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.core.SlidevGlobs
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.project.SlidevWorkspaceScanner
import dev.slidev.intellij.settings.SlidevSettings

/**
 * Provides the preview half of the Slidev split editor for deck entry files: registered
 * entries and files matching the include/exclude globs (so it works before the startup
 * scan completes). Imported `src:` files are not entries and get no split editor.
 */
internal class SlidevPreviewFileEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (file.isDirectory || !file.name.endsWith(".md", ignoreCase = true)) {
            return false
        }
        if (SlidevProjectService.getInstance(project).stateFor(file.path) != null) {
            return true
        }
        val settings = SlidevSettings.getInstance(project).state
        val globs = SlidevGlobs(settings.include, settings.exclude)
        return SlidevWorkspaceScanner.matches(ProjectFileIndex.getInstance(project), globs, file)
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        SlidevPreviewFileEditor(project, file)

    override fun getEditorTypeId(): String = "slidev-preview-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

/**
 * The Slidev split editor (text + live preview), the same mechanism the bundled Markdown
 * plugin uses for its preview; the platform persists the chosen layout per file.
 */
internal class SlidevSplitEditorProvider : TextEditorWithPreviewProvider(SlidevPreviewFileEditorProvider()) {

    override fun createSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor =
        TextEditorWithPreview(firstEditor, secondEditor, SlidevBundle.message("editor.split.name"))
}
