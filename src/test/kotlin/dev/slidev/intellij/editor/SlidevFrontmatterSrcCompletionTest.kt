package dev.slidev.intellij.editor

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Platform tests for path completion in frontmatter `src:` import values: relative
 * and root-relative flavors, containment matching, quote closing, the page-range
 * bail, and the excluded directories.
 */
class SlidevFrontmatterSrcCompletionTest : BasePlatformTestCase() {

    private fun configure(text: String, vararg extraFiles: Pair<String, String>) {
        configureCaretFile("slides.md", text, *extraFiles)
    }

    private fun configureCaretFile(caretPath: String, text: String, vararg extraFiles: Pair<String, String>) {
        for ((path, content) in extraFiles) {
            myFixture.addFileToProject(path, content)
        }
        val psiFile = myFixture.addFileToProject(caretPath, text)
        SlidevProjectService.getInstance(project).rescanSync()
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    }

    private fun lookupStrings(): List<String> =
        myFixture.completeBasic()?.map { it.lookupString }.orEmpty()

    fun `test empty prefix offers importable files but not the file itself`() {
        configure(
            """
            # First

            ---
            src: <caret>
            ---
            """.trimIndent(),
            "pages/intro.md" to "# Intro",
            "pages/outro.md" to "# Outro",
        )
        val lookups = lookupStrings()
        assertContainsElements(lookups, "./pages/intro.md", "./pages/outro.md")
        assertDoesntContain(lookups, "./slides.md")
    }

    fun `test relative prefix narrows to matching files`() {
        configure(
            """
            # First

            ---
            src: ./pa<caret>
            ---
            """.trimIndent(),
            "pages/intro.md" to "# Intro",
            "pages/outro.md" to "# Outro",
        )
        assertContainsElements(lookupStrings(), "./pages/intro.md", "./pages/outro.md")
    }

    fun `test root-relative prefix offers root-anchored items`() {
        configure(
            """
            # First

            ---
            src: /pa<caret>
            ---
            """.trimIndent(),
            "pages/intro.md" to "# Intro",
            "pages/outro.md" to "# Outro",
        )
        val lookups = lookupStrings()
        assertContainsElements(lookups, "/pages/intro.md", "/pages/outro.md")
        assertDoesntContain(lookups, "./pages/intro.md")
    }

    fun `test prefix without leading dot-slash matches by containment`() {
        configure(
            """
            # First

            ---
            src: pages/in<caret>
            ---
            """.trimIndent(),
            "pages/intro.md" to "# Intro",
            "pages/inside.md" to "# Inside",
        )
        assertContainsElements(lookupStrings(), "./pages/intro.md", "./pages/inside.md")
    }

    fun `test importer in subdirectory gets step-up and sibling paths`() {
        configureCaretFile(
            "pages/section.md",
            """
            # Section

            ---
            src: <caret>
            ---
            """.trimIndent(),
            "intro.md" to "# Intro",
            "pages/more.md" to "# More",
            "slides.md" to
                """
                # First

                ---
                src: ./pages/section.md
                ---
                """.trimIndent(),
        )
        assertContainsElements(lookupStrings(), "../intro.md", "./more.md")
    }

    fun `test completing inside quotes closes the quote`() {
        configure(
            """
            # First

            ---
            src: "./pages/in<caret>
            ---
            """.trimIndent(),
            "pages/intro.md" to "# Intro",
        )
        val items = myFixture.completeBasic()
        if (items != null) {
            // Other contributors kept the lookup open; select the path item explicitly.
            val item = items.first { it.lookupString == "./pages/intro.md" }
            (myFixture.lookup as LookupImpl).currentItem = item
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        }
        assertTrue(myFixture.editor.document.text.contains("src: \"./pages/intro.md\""))
    }

    fun `test no path items while typing a page range`() {
        configure(
            """
            # First

            ---
            src: ./pages/intro.md#2<caret>
            ---
            """.trimIndent(),
            "pages/intro.md" to "# Intro",
            "pages/outro.md" to "# Outro",
        )
        assertDoesntContain(lookupStrings(), "./pages/intro.md", "./pages/outro.md")
    }

    fun `test node_modules dot directories and non-markdown files are not offered`() {
        configure(
            """
            # First

            ---
            src: <caret>
            ---
            """.trimIndent(),
            "pages/intro.md" to "# Intro",
            "pages/outro.md" to "# Outro",
            "pages/image.png" to "png",
            "node_modules/dep/readme.md" to "# Dep",
            ".git/notes.md" to "# Notes",
        )
        val lookups = lookupStrings()
        assertContainsElements(lookups, "./pages/intro.md")
        assertDoesntContain(lookups, "./node_modules/dep/readme.md", "./.git/notes.md", "./pages/image.png")
    }

    fun `test headmatter src completion in injected yaml`() {
        configure(
            """
            ---
            src: ./pa<caret>
            ---

            # First
            """.trimIndent(),
            "pages/intro.md" to "# Intro",
            "pages/outro.md" to "# Outro",
        )
        assertContainsElements(lookupStrings(), "./pages/intro.md")
    }
}
