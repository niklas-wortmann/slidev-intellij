package dev.slidev.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.project.SlidevListener
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.project.SlidevProjectState
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Flat tree of all registered Slidev projects, the counterpart of the "Projects"
 * tree view in the VS Code extension. The active entry is marked with an eye icon;
 * the context menu sets the active entry or removes one, and double-clicking opens
 * the entry file in the editor.
 */
class SlidevProjectsPanel(private val project: Project) : JPanel(BorderLayout()), UiDataProvider, Disposable {

    private val root = DefaultMutableTreeNode()
    private val model = DefaultTreeModel(root)
    private val tree = Tree(model)

    init {
        val toolbar = ActionManager.getInstance().createActionToolbar(
            "SlidevProjects",
            ActionManager.getInstance().getAction("Slidev.ProjectsToolbar") as ActionGroup,
            true,
        )
        toolbar.targetComponent = this
        add(toolbar.component, BorderLayout.NORTH)

        tree.isRootVisible = false
        tree.showsRootHandles = false
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.emptyText.text = SlidevBundle.message("projects.tree.empty")
        tree.cellRenderer = ProjectRenderer()
        PopupHandler.installPopupMenu(tree, "Slidev.ProjectsContext", "SlidevProjectsPopup")
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val state = selectedState() ?: return false
                OpenFileDescriptor(project, state.entryFile, 0, 0).navigate(true)
                return true
            }
        }.installOn(tree)
        add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)

        project.messageBus.connect(this).subscribe(
            SlidevListener.TOPIC,
            object : SlidevListener {
                override fun projectsChanged() = rebuild()
                override fun dataReloaded(state: SlidevProjectState) = rebuild()
                override fun serverChanged(state: SlidevProjectState) = rebuild()
            },
        )
        rebuild()
    }

    override fun uiDataSnapshot(sink: DataSink) {
        sink[SlidevDataKeys.PROJECT_STATE] = selectedState()
    }

    private fun rebuild() {
        val previous = selectedState()?.entryPath
        root.removeAllChildren()
        for (state in SlidevProjectService.getInstance(project).projects()) {
            root.add(DefaultMutableTreeNode(state))
        }
        model.reload()
        previous?.let(::selectEntry)
    }

    private fun selectEntry(entryPath: String) {
        for (i in 0 until root.childCount) {
            val node = root.getChildAt(i) as DefaultMutableTreeNode
            if ((node.userObject as? SlidevProjectState)?.entryPath == entryPath) {
                tree.selectionPath = TreePath(node.path)
                return
            }
        }
    }

    private fun selectedState(): SlidevProjectState? =
        (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? SlidevProjectState

    private fun relativePath(state: SlidevProjectState): String {
        val base = project.guessProjectDir() ?: return state.entryPath
        return VfsUtilCore.getRelativePath(state.entryFile, base) ?: state.entryPath
    }

    private inner class ProjectRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            val state = (value as? DefaultMutableTreeNode)?.userObject as? SlidevProjectState ?: return
            val active = state.entryPath == SlidevProjectService.getInstance(project).activeEntryPath
            icon = if (active) AllIcons.Actions.Show else AllIcons.FileTypes.Text
            append(
                relativePath(state),
                if (active) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES,
            )
            if (state.title != state.entryFile.name) {
                append(" " + state.title, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            state.port?.let {
                append(" " + SlidevBundle.message("projects.tree.port", it), SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }

    override fun dispose() {}
}
