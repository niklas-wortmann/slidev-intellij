package dev.slidev.intellij.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Platform smoke tests for the schema-driven frontmatter support: completion of
 * keys and enum values, slide-0 vs slide-n schema selection, and validation.
 */
class SlidevFrontmatterSupportTest : BasePlatformTestCase() {

    private fun configure(text: String) {
        val psiFile = myFixture.addFileToProject("slides.md", text)
        SlidevProjectService.getInstance(project).rescanSync()
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    }

    fun `test key completion in slide frontmatter`() {
        configure(
            """
            # First

            ---
            lay<caret>
            ---

            second
            """.trimIndent(),
        )
        val lookups = myFixture.completeBasic()?.map { it.lookupString }
        if (lookups == null) {
            // The single matching item was auto-inserted.
            assertTrue(myFixture.editor.document.text.contains("layout: "))
        }
        else {
            assertContainsElements(lookups, "layout")
            // Deck-level options must not leak into slide frontmatter.
            assertDoesntContain(lookups, "theme")
        }
    }

    fun `test headmatter completion offers deck options on slide 0`() {
        configure(
            """
            ---
            th<caret>
            ---

            # First
            """.trimIndent(),
        )
        val lookups = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(lookups, "theme")
    }

    fun `test enum value completion for layout`() {
        configure(
            """
            # First

            ---
            layout: <caret>
            ---

            second
            """.trimIndent(),
        )
        val lookups = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertContainsElements(lookups, "cover", "two-cols", "image-right")
    }

    fun `test no completion outside frontmatter`() {
        configure(
            """
            # First

            lay<caret>

            ---
            layout: cover
            ---

            second
            """.trimIndent(),
        )
        val lookups = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertDoesntContain(lookups, "layout")
    }

    fun `test no completion in non slidev markdown`() {
        val psiFile = myFixture.addFileToProject(
            "notes/readme.md",
            """
            # Hello

            ---
            lay<caret>
            ---
            """.trimIndent(),
        )
        SlidevProjectService.getInstance(project).rescanSync()
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
        val lookups = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertDoesntContain(lookups, "layout")
    }

    fun `test annotator flags type mismatches and yaml errors`() {
        configure(
            """
            ---
            title: Demo
            ---

            # First

            ---
            hide: maybe
            ---

            second
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        val warning = highlights.firstOrNull { it.description?.contains("hide") == true }
        assertNotNull("expected a warning for hide: maybe", warning)
    }

    fun `test annotator flags yaml syntax errors`() {
        configure(
            """
            # First

            ---
            layout: [unclosed
            ---

            second
            """.trimIndent(),
        )
        val errors = myFixture.doHighlighting()
        assertTrue(errors.any { it.description?.startsWith("Invalid YAML") == true })
    }

    fun `test annotator accepts valid frontmatter`() {
        configure(
            """
            ---
            title: Demo
            theme: seriph
            ---

            # First

            ---
            layout: my-custom-layout
            clicks: 3
            hide: true
            ---

            second
            """.trimIndent(),
        )
        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertEmpty(descriptions.filter { it.startsWith("Invalid") || it.startsWith("Frontmatter") })
    }
}
