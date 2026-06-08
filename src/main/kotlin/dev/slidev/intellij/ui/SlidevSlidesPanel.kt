package dev.slidev.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.RowsDnDSupport
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.EditableModel
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.actions.SlidevChooseEntryAction
import dev.slidev.intellij.core.SlideNavigation
import dev.slidev.intellij.core.SlideReorder
import dev.slidev.intellij.parser.ResolvedSlide
import dev.slidev.intellij.project.SlidevListener
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.project.SlidevProjectState
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Flat tree of the active project's resolved slides, the counterpart of the
 * "Slides" tree view in the VS Code extension. Selection follows the editor
 * caret; selecting a slide reveals its source in the editor (which in turn
 * drives the preview through the caret-sync pipeline). Slides can be reordered
 * by drag and drop, which rewrites the markdown source like the VS Code tree;
 * unlike there, moves are restricted to a single source file so `src:` imports
 * with `#range` suffixes cannot silently break.
 */
class SlidevSlidesPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val root = DefaultMutableTreeNode()
    private val model = SlidesTreeModel()
    private val tree = Tree(model)
    private var ignoreEvents = false

    /** Source slide `(filepath, index)` to select once the post-reorder reload lands. */
    private var pendingSelection: Pair<String, Int>? = null

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = false
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.emptyText.text = SlidevBundle.message("slides.tree.empty")
        tree.emptyText.appendLine(
            SlidevBundle.message("slides.tree.empty.choose"),
            SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
        ) { SlidevChooseEntryAction.showPopup(project) }
        tree.cellRenderer = SlideRenderer()
        tree.addTreeSelectionListener {
            if (!ignoreEvents) {
                selectedSlide()?.let(::navigateToSlide)
            }
        }
        RowsDnDSupport.install(tree, model)
        add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)

        project.messageBus.connect(this).subscribe(
            SlidevListener.TOPIC,
            object : SlidevListener {
                override fun projectsChanged() = rebuild()
                override fun dataReloaded(state: SlidevProjectState) {
                    if (state.entryPath == SlidevProjectService.getInstance(project).activeEntryPath) {
                        rebuild()
                    }
                }
            },
        )
        EditorFactory.getInstance().eventMulticaster.addCaretListener(FollowCaretListener(), this)
        rebuild()
    }

    private fun rebuild() {
        val previous = selectedSlide()?.no
        root.removeAllChildren()
        val slides = SlidevProjectService.getInstance(project).activeState()?.data?.slides.orEmpty()
        for (slide in slides) {
            root.add(DefaultMutableTreeNode(slide))
        }
        model.reload()

        val pending = pendingSelection
        pendingSelection = null
        val moved = pending?.let { (path, index) ->
            slides.firstOrNull { it.source.filepath == path && it.source.index == index }
        }
        if (moved != null) {
            selectSlide(moved.no)
        }
        else {
            previous?.let { selectSlide(it) }
        }
    }

    /** Programmatic selection used by caret sync; never navigates back to the editor. */
    fun selectSlide(no: Int) {
        val index = no - 1
        if (index < 0 || index >= root.childCount) {
            return
        }
        ignoreEvents = true
        try {
            val path = TreePath((root.getChildAt(index) as DefaultMutableTreeNode).path)
            tree.selectionPath = path
            tree.scrollPathToVisible(path)
        }
        finally {
            ignoreEvents = false
        }
    }

    private fun selectedSlide(): ResolvedSlide? =
        (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? ResolvedSlide

    private fun resolvedAt(row: Int): ResolvedSlide? =
        if (row in 0 until root.childCount) {
            (root.getChildAt(row) as DefaultMutableTreeNode).userObject as? ResolvedSlide
        }
        else {
            null
        }

    /** Rewrites the source files so [dragged] ends up right after [target], as one undoable command. */
    internal fun performMove(dragged: ResolvedSlide, target: ResolvedSlide) {
        val state = SlidevProjectService.getInstance(project).activeState() ?: return
        val data = state.data ?: return
        val result = SlideReorder.computeMove(data, listOf(dragged.source), target.source.filepath, target.source.index)
            ?: return
        val documents = result.changes.mapNotNull { (path, text) ->
            state.entryFile.fileSystem.findFileByPath(path)
                ?.let(FileDocumentManager.getInstance()::getDocument)
                ?.let { it to text }
        }
        if (documents.size < result.changes.size) {
            return
        }
        pendingSelection = target.source.filepath to result.insertedAt
        WriteCommandAction.runWriteCommandAction(project, SlidevBundle.message("slides.reorder.command"), null, {
            documents.forEach { (document, text) -> document.setText(text) }
        })
    }

    /**
     * Flat tree model with row drag-and-drop: visible row n is root child n, so the
     * [RowsDnDSupport.RefinedDropSupport] row indices map straight onto resolved slides.
     */
    private inner class SlidesTreeModel : DefaultTreeModel(root), EditableModel, RowsDnDSupport.RefinedDropSupport {

        override fun isDropInto(component: JComponent, oldIndex: Int, newIndex: Int): Boolean = false

        override fun canDrop(oldIndex: Int, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position): Boolean {
            if (position == RowsDnDSupport.RefinedDropSupport.Position.INTO) {
                return false
            }
            val dragged = resolvedAt(oldIndex) ?: return false
            val target = resolvedAt(targetRow(newIndex, position)) ?: return false
            return dragged.source.filepath == target.source.filepath &&
                !SlideReorder.sameSlide(dragged.source, target.source) &&
                dragged.source.index != target.source.index + 1
        }

        override fun drop(oldIndex: Int, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position) {
            val dragged = resolvedAt(oldIndex) ?: return
            val target = resolvedAt(targetRow(newIndex, position)) ?: return
            performMove(dragged, target)
        }

        /** The row of the slide the drag gets inserted *after*; dropping above row 0 is rejected. */
        private fun targetRow(newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position): Int =
            if (position == RowsDnDSupport.RefinedDropSupport.Position.ABOVE) newIndex - 1 else newIndex

        override fun addRow(): Unit = throw UnsupportedOperationException()

        override fun removeRow(idx: Int): Unit = throw UnsupportedOperationException()

        override fun exchangeRows(oldIndex: Int, newIndex: Int): Unit = throw UnsupportedOperationException()

        override fun canExchangeRows(oldIndex: Int, newIndex: Int): Boolean = false
    }

    private fun navigateToSlide(slide: ResolvedSlide) {
        val file = LocalFileSystem.getInstance().findFileByPath(slide.source.filepath) ?: return
        OpenFileDescriptor(project, file, slide.source.contentStart, 0).navigate(true)
    }

    private inner class FollowCaretListener : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            val editor = event.editor
            if (editor.project !== project) {
                return
            }
            val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
            val data = SlidevProjectService.getInstance(project).activeState()?.data ?: return
            val slide = SlideNavigation.resolvedSlideForLine(data, file.path, event.newPosition.line) ?: return
            if (selectedSlide()?.no != slide.no) {
                selectSlide(slide.no)
            }
        }
    }

    private inner class SlideRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            val slide = (value as? DefaultMutableTreeNode)?.userObject as? ResolvedSlide ?: return
            append("${slide.no}. ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            append(slide.title ?: SlidevBundle.message("slides.tree.untitled"))
            if (slide.source.isHidden) {
                append(" " + SlidevBundle.message("slides.tree.hidden"), SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }

    override fun dispose() {}
}
