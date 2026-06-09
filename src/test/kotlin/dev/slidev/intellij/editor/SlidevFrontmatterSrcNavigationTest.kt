package dev.slidev.intellij.editor

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Platform tests for go-to-declaration from frontmatter `src:` import values to the
 * imported markdown file, covering relative paths, `#range` suffixes, root-relative
 * paths, and the non-navigable cases.
 */
class SlidevFrontmatterSrcNavigationTest : BasePlatformTestCase() {

    private val handler = SlidevFrontmatterSrcGotoDeclarationHandler()

    private fun configure(text: String, vararg extraFiles: Pair<String, String>) {
        for ((path, content) in extraFiles) {
            myFixture.addFileToProject(path, content)
        }
        val psiFile = myFixture.addFileToProject("slides.md", text)
        SlidevProjectService.getInstance(project).rescanSync()
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    }

    private fun targetAtCaret(): PsiFile? {
        val offset = myFixture.caretOffset
        val element = myFixture.file.findElementAt(offset)
        val targets = handler.getGotoDeclarationTargets(element, offset, myFixture.editor)
        return targets?.singleOrNull() as? PsiFile
    }

    fun `test navigates from relative src value to imported file`() {
        configure(
            """
            # First

            ---
            src: ./pages/impo<caret>rted.md
            ---
            """.trimIndent(),
            "pages/imported.md" to "# Imported",
        )
        assertEquals("imported.md", targetAtCaret()?.name)
    }

    fun `test navigates with page range suffix`() {
        configure(
            """
            # First

            ---
            src: ./pages/imported.md#2,<caret>5-7
            ---
            """.trimIndent(),
            "pages/imported.md" to "# A\n\n---\n\n# B",
        )
        assertEquals("imported.md", targetAtCaret()?.name)
    }

    fun `test navigates from root-relative src value`() {
        configure(
            """
            # First

            ---
            src: /pages/impor<caret>ted.md
            ---
            """.trimIndent(),
            "pages/imported.md" to "# Imported",
        )
        assertEquals("imported.md", targetAtCaret()?.name)
    }

    fun `test no navigation when target file is missing`() {
        configure(
            """
            # First

            ---
            src: ./mis<caret>sing.md
            ---
            """.trimIndent(),
        )
        assertNull(targetAtCaret())
    }

    fun `test no navigation on other keys or outside frontmatter`() {
        configure(
            """
            # First

            ---
            layout: co<caret>ver
            ---

            see ./pages/imported.md
            """.trimIndent(),
            "pages/imported.md" to "# Imported",
        )
        assertNull(targetAtCaret())

        // Caret on the path mentioned in plain slide content.
        val offset = myFixture.editor.document.text.indexOf("./pages/imported.md")
        val element = myFixture.file.findElementAt(offset)
        assertNull(handler.getGotoDeclarationTargets(element, offset, myFixture.editor))
    }

    fun `test no navigation from the src key itself`() {
        configure(
            """
            # First

            ---
            sr<caret>c: ./pages/imported.md
            ---
            """.trimIndent(),
            "pages/imported.md" to "# Imported",
        )
        assertNull(targetAtCaret())
    }
}
