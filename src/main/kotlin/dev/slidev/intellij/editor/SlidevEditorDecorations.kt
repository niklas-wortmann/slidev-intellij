package dev.slidev.intellij.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import dev.slidev.intellij.parser.findCodeBlocks
import dev.slidev.intellij.project.SlidevListener
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.project.SlidevProjectState
import dev.slidev.intellij.settings.SlidevSettings
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle

/**
 * Owns the markup-model decorations of Slidev markdown editors, the counterpart of the
 * decoration types in `annotations.ts` of the VS Code extension: a subtle background
 * tint on classic frontmatter blocks, inline parse/load error messages, and virtual
 * line numbers inside fenced code blocks. The slide-number decorations live in
 * [SlidevSlideLinePainter]. Like there, everything is painted from the debounced
 * service data and clamped against the (possibly newer) document; stale decorations
 * self-correct on the next reload event.
 */
@Service(Service.Level.PROJECT)
class SlidevEditorDecorations(private val project: Project) : Disposable {

    /** Called once from the startup activity. */
    fun initialize() {
        EditorFactory.getInstance().addEditorFactoryListener(
            object : EditorFactoryListener {
                override fun editorCreated(event: EditorFactoryEvent) {
                    if (event.editor.project == project) {
                        refresh(event.editor)
                    }
                }
            },
            this,
        )
        project.messageBus.connect(this).subscribe(
            SlidevListener.TOPIC,
            object : SlidevListener {
                override fun dataReloaded(state: SlidevProjectState) = refreshAll()
                override fun settingsChanged() = refreshAll()
            },
        )
        refreshAll()
    }

    private fun refreshAll() {
        EditorFactory.getInstance().allEditors
            .filter { it.project == project }
            .forEach(::refresh)
    }

    /** Recomputes the decorations of one editor; runs on the EDT, cheap lookups only. */
    fun refresh(editor: Editor) {
        clear(editor)

        val settings = SlidevSettings.getInstance(project).state
        if (!settings.annotations) {
            return
        }
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        if (!file.name.endsWith(".md", ignoreCase = true)) {
            return
        }
        val service = SlidevProjectService.getInstance(project)
        val state = service.stateContaining(file.path) ?: return
        val md = service.markdownFor(file.path)

        val highlighters = mutableListOf<RangeHighlighter>()
        val inlays = mutableListOf<Inlay<*>>()
        val document = editor.document
        val lastLine = document.lineCount - 1

        // Frontmatter tint, like the `#8881` background decoration of annotations.ts.
        if (md != null) {
            for (slide in md.slides) {
                val lines = slide.frontmatterLines ?: continue
                for (line in lines) {
                    if (line > lastLine) {
                        break
                    }
                    highlighters.add(
                        editor.markupModel.addLineHighlighter(
                            line,
                            HighlighterLayer.CARET_ROW - 1,
                            TextAttributes(null, FRONTMATTER_BACKGROUND, null, null, Font.PLAIN),
                        ),
                    )
                }
            }
        }

        // Inline error messages at the offending line, like the error decorations of annotations.ts.
        val errorAttributes = editor.colorsScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)
        for (error in state.data?.errors?.get(file.path).orEmpty()) {
            val line = error.row.coerceIn(0, lastLine)
            highlighters.add(editor.markupModel.addLineHighlighter(line, HighlighterLayer.ERROR, errorAttributes))
            editor.inlayModel.addAfterLineEndElement(
                document.getLineEndOffset(line),
                false,
                ErrorMessageRenderer(error.message),
            )?.let(inlays::add)
        }

        // Virtual line numbers in fenced code blocks, like the `annotations-line-numbers` setting.
        if (settings.annotationsLineNumbers) {
            val lines = document.charsSequence.split("\n")
            for (block in findCodeBlocks(lines)) {
                val width = (block.endLine - block.startLine).toString().length
                for ((n, line) in (block.startLine until minOf(block.endLine, lastLine + 1)).withIndex()) {
                    val lineStart = document.getLineStartOffset(line)
                    val lineLength = document.getLineEndOffset(line) - lineStart
                    editor.inlayModel.addInlineElement(
                        lineStart + minOf(block.indent, lineLength),
                        false,
                        LineNumberRenderer(n + 1, width),
                    )?.let(inlays::add)
                }
            }
        }

        editor.putUserData(DECORATIONS_KEY, DecorationSet(highlighters, inlays))
    }

    private fun clear(editor: Editor) {
        val old = editor.getUserData(DECORATIONS_KEY) ?: return
        editor.putUserData(DECORATIONS_KEY, null)
        old.highlighters.forEach(editor.markupModel::removeHighlighter)
        old.inlays.forEach(Inlay<*>::dispose)
    }

    override fun dispose() {
        EditorFactory.getInstance().allEditors
            .filter { it.project == project }
            .forEach(::clear)
    }

    private class DecorationSet(
        val highlighters: List<RangeHighlighter>,
        val inlays: List<Inlay<*>>,
    )

    /** Paints ` <message>` after the line end in error colors. */
    private class ErrorMessageRenderer(private val message: String) : EditorCustomElementRenderer {
        private fun text() = " $message"

        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val editor = inlay.editor
            val metrics = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.ITALIC))
            return metrics.stringWidth(text())
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            val editor = inlay.editor
            val scheme = editor.colorsScheme
            g.font = scheme.getFont(EditorFontType.ITALIC)
            g.color = scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)?.errorStripeColor ?: JBColor.RED
            g.drawString(text(), targetRegion.x, targetRegion.y + editor.ascent)
        }
    }

    /** Paints a right-aligned `nn│ ` line number before a code-block line. */
    private class LineNumberRenderer(private val number: Int, width: Int) : EditorCustomElementRenderer {
        private val text = "%${width}d│ ".format(number)

        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val editor = inlay.editor
            val metrics = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
            return metrics.stringWidth(text)
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            val editor = inlay.editor
            val scheme = editor.colorsScheme
            g.font = scheme.getFont(EditorFontType.PLAIN)
            g.color = scheme.getColor(EditorColors.LINE_NUMBERS_COLOR) ?: JBColor.GRAY
            g.drawString(text, targetRegion.x, targetRegion.y + editor.ascent)
        }
    }

    companion object {
        private val DECORATIONS_KEY = Key.create<DecorationSet>("slidev.editor.decorations")

        /** The IntelliJ analog of the `#8881` frontmatter tint in the VS Code extension. */
        private val FRONTMATTER_BACKGROUND = JBColor(Color(0x88, 0x88, 0x88, 0x18), Color(0x88, 0x88, 0x88, 0x18))

        @JvmStatic
        fun getInstance(project: Project): SlidevEditorDecorations = project.service()
    }
}
