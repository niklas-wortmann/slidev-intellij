package dev.slidev.intellij.ui.preview

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Tests the split-editor `accept` logic only; `createEditor` needs JCEF and is
 * exercised manually via runIde.
 */
class SlidevPreviewFileEditorProviderTest : BasePlatformTestCase() {

    private val provider = SlidevPreviewFileEditorProvider()

    private val service: SlidevProjectService
        get() = SlidevProjectService.getInstance(project)

    override fun tearDown() {
        try {
            service.projects().forEach { service.removeEntry(it.entryPath) }
        }
        catch (e: Throwable) {
            addSuppressedException(e)
        }
        finally {
            super.tearDown()
        }
    }

    fun `test accepts glob-matching entry without a workspace scan`() {
        val file = myFixture.addFileToProject("slides.md", "# Deck").virtualFile

        // No rescan: accept() must not depend on the async startup scan having run.
        assertTrue(provider.accept(project, file))
    }

    fun `test accepts nested glob-matching entry`() {
        val file = myFixture.addFileToProject("docs/slides.md", "# Deck").virtualFile

        assertTrue(provider.accept(project, file))
    }

    fun `test rejects non-entry markdown and non-markdown files`() {
        val readme = myFixture.addFileToProject("README.md", "# Not a deck").virtualFile
        val text = myFixture.addFileToProject("notes.txt", "notes").virtualFile

        assertFalse(provider.accept(project, readme))
        assertFalse(provider.accept(project, text))
    }

    fun `test rejects excluded node_modules entries`() {
        val file = myFixture.addFileToProject("node_modules/pkg/slides.md", "# Ignored").virtualFile

        assertFalse(provider.accept(project, file))
    }

    fun `test accepts manually registered entry with non-glob name`() {
        val file = myFixture.addFileToProject("decks/talk.md", "# Talk").virtualFile
        assertFalse(provider.accept(project, file))

        service.addEntry(file)

        assertTrue(provider.accept(project, file))
    }

    fun `test rejects imported src files`() {
        myFixture.addFileToProject("slides.md", "---\nsrc: ./other.md\n---\n")
        val other = myFixture.addFileToProject("other.md", "# Imported").virtualFile
        service.rescanSync()
        assertNotNull(service.stateContaining(other.path))

        assertFalse(provider.accept(project, other))
    }

    fun `test split provider accepts entry files`() {
        val file = myFixture.addFileToProject("slides.md", "# Deck").virtualFile

        assertTrue(SlidevSplitEditorProvider().accept(project, file))
    }
}
