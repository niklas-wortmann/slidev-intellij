package dev.slidev.intellij.editor

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.platform.backend.documentation.impl.computeDocumentationBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Integration tests for the frontmatter quick documentation, going through
 * [IdeDocumentationTargetProvider] — the same entry point the hover popup and
 * the completion-lookup documentation panel use.
 */
class SlidevFrontmatterDocumentationTest : BasePlatformTestCase() {

    private fun configure(text: String) {
        val psiFile = myFixture.addFileToProject("slides.md", text)
        SlidevProjectService.getInstance(project).rescanSync()
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    }

    private fun docAtCaret(): String? {
        val targets = IdeDocumentationTargetProvider.getInstance(project)
            .documentationTargets(myFixture.editor, myFixture.file, myFixture.caretOffset)
        return targets.firstNotNullOfOrNull { computeDocumentationBlocking(it.createPointer())?.html }
    }

    fun `test hover documentation on frontmatter key`() {
        configure(
            """
            # First

            ---
            lay<caret>out: cover
            ---

            second
            """.trimIndent(),
        )
        val html = docAtCaret()
        assertNotNull("expected hover documentation for 'layout'", html)
        assertTrue(html!!.contains("layout"))
    }

    fun `test hover documentation on frontmatter value`() {
        configure(
            """
            # First

            ---
            layout: cov<caret>er
            ---

            second
            """.trimIndent(),
        )
        val html = docAtCaret()
        assertNotNull("expected hover documentation over the value of 'layout'", html)
        assertTrue(html!!.contains("layout"))
    }

    fun `test documentation for completion lookup item`() {
        configure(
            """
            # First

            ---
            l<caret>
            ---

            second
            """.trimIndent(),
        )
        // Prefix "l" matches both "layout" and "level", so the lookup is guaranteed to show.
        myFixture.completeBasic()
        val lookupElement = myFixture.lookupElements?.firstOrNull { it.lookupString == "layout" }
        assertNotNull("expected a 'layout' lookup element", lookupElement)
        val target = IdeDocumentationTargetProvider.getInstance(project)
            .documentationTargets(myFixture.editor, myFixture.file, lookupElement!!)
            .firstOrNull()
        assertNotNull("expected a documentation target for the 'layout' lookup item", target)
        val html = computeDocumentationBlocking(target!!.createPointer())?.html
        assertNotNull("expected documentation html for the 'layout' lookup item", html)
        assertTrue(html!!.contains("layout"))
    }
}
