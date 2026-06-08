package dev.slidev.intellij.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.settings.SlidevSettings

class SlidevEditorDecorationsTest : BasePlatformTestCase() {

    private val decorations: SlidevEditorDecorations
        get() = SlidevEditorDecorations.getInstance(project)

    override fun setUp() {
        super.setUp()
        // The light test project is reused between tests; restore the defaults.
        SlidevSettings.getInstance(project).state.apply {
            annotations = true
            annotationsLineNumbers = true
        }
    }

    private fun openDeck(): com.intellij.openapi.editor.Editor {
        val psiFile = myFixture.addFileToProject(
            "slides.md",
            """
            # Title

            ---
            src: ./missing.md
            ---

            ```ts
            const a = 1
            const b = 2
            ```
            """.trimIndent(),
        )
        SlidevProjectService.getInstance(project).rescanSync()
        myFixture.openFileInEditor(psiFile.virtualFile)
        return myFixture.editor
    }

    fun `test frontmatter tint and error message and code block line numbers`() {
        val editor = openDeck()
        decorations.refresh(editor)

        // Three tinted frontmatter lines (2..4) plus one error line highlighter.
        val lines = editor.markupModel.allHighlighters.map { editor.document.getLineNumber(it.startOffset) }.sorted()
        assertEquals(listOf(2, 2, 3, 4), lines)

        // The missing-import error message after line 2.
        val errorInlays = editor.inlayModel.getAfterLineEndElementsInRange(0, editor.document.textLength)
        assertEquals(1, errorInlays.size)
        assertEquals(2, editor.document.getLineNumber(errorInlays.single().offset))

        // Line numbers before the two interior code-block lines (7 and 8).
        val lineNumberInlays = editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength)
        assertEquals(
            listOf(7, 8),
            lineNumberInlays.map { editor.document.getLineNumber(it.offset) },
        )
    }

    fun `test annotations setting disables all decorations`() {
        val editor = openDeck()
        SlidevSettings.getInstance(project).state.annotations = false
        decorations.refresh(editor)

        assertEmpty(editor.markupModel.allHighlighters)
        assertEmpty(editor.inlayModel.getAfterLineEndElementsInRange(0, editor.document.textLength))
        assertEmpty(editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength))
    }

    fun `test line numbers setting disables only the code block inlays`() {
        val editor = openDeck()
        SlidevSettings.getInstance(project).state.annotationsLineNumbers = false
        decorations.refresh(editor)

        assertNotEmpty(editor.markupModel.allHighlighters.toList())
        assertEmpty(editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength))
    }

    fun `test refresh replaces previous decorations instead of stacking them`() {
        val editor = openDeck()
        decorations.refresh(editor)
        val countAfterFirst = editor.markupModel.allHighlighters.size
        decorations.refresh(editor)

        assertEquals(countAfterFirst, editor.markupModel.allHighlighters.size)
        assertEquals(1, editor.inlayModel.getAfterLineEndElementsInRange(0, editor.document.textLength).size)
    }

    fun `test unregistered markdown files are not decorated`() {
        val psiFile = myFixture.addFileToProject("README.md", "---\nkey: value\n---\n# Readme")
        SlidevProjectService.getInstance(project).rescanSync()
        myFixture.openFileInEditor(psiFile.virtualFile)
        decorations.refresh(myFixture.editor)

        assertEmpty(myFixture.editor.markupModel.allHighlighters)
    }
}
