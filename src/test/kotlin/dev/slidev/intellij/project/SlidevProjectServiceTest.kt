package dev.slidev.intellij.project

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.slidev.intellij.settings.SlidevWorkspaceState

class SlidevProjectServiceTest : BasePlatformTestCase() {

    private val service: SlidevProjectService
        get() = SlidevProjectService.getInstance(project)

    fun `test rescan finds glob-matching entries and excludes node_modules`() {
        myFixture.addFileToProject("slides.md", "# Root Deck")
        myFixture.addFileToProject("docs/slides.md", "# Docs Deck")
        myFixture.addFileToProject("node_modules/pkg/slides.md", "# Ignored")
        myFixture.addFileToProject("README.md", "# Not a deck")

        service.rescanSync()

        val entries = service.projects().map { it.entryFile.name to it.entryPath }
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.first == "slides.md" })
        assertFalse(entries.any { it.second.contains("node_modules") })
    }

    fun `test shallowest slides md becomes the active project`() {
        // Shallow file first: each file creation triggers an async rescan via the VFS listener,
        // and the first pick is sticky — creating the deep file first makes this test flaky.
        myFixture.addFileToProject("a/slides.md", "# Shallow")
        myFixture.addFileToProject("a/b/slides.md", "# Deep")

        service.rescanSync()

        assertEquals("a/slides.md", service.activeState()?.entryPath?.substringAfter("/src/"))
    }

    fun `test rescan loads slide data`() {
        myFixture.addFileToProject("slides.md", "# One\n\n---\n\n# Two")

        service.rescanSync()

        val data = service.activeState()?.data
        assertNotNull(data)
        assertEquals(listOf("One", "Two"), data!!.slides.map { it.title })
        assertEquals("One", service.activeState()?.title)
    }

    fun `test add and remove entry persist to workspace state`() {
        val file = myFixture.addFileToProject("decks/talk.md", "# Talk").virtualFile

        service.addEntry(file)
        assertEquals(listOf(file.path), SlidevWorkspaceState.getInstance(project).state.entries)
        assertEquals(file.path, SlidevWorkspaceState.getInstance(project).state.activeEntry)

        service.removeEntry(file.path)
        assertEmpty(SlidevWorkspaceState.getInstance(project).state.entries)
        assertNull(SlidevWorkspaceState.getInstance(project).state.activeEntry)
    }

    fun `test set active switches the active project`() {
        myFixture.addFileToProject("slides.md", "# Root")
        val other = myFixture.addFileToProject("docs/slides.md", "# Docs").virtualFile
        service.rescanSync()
        assertNotSame(other.path, service.activeEntryPath)

        service.setActive(other.path)

        assertEquals(other.path, service.activeEntryPath)
        assertEquals(other.path, SlidevWorkspaceState.getInstance(project).state.activeEntry)
    }

    fun `test unsaved document text is preferred when reloading`() {
        val psiFile = myFixture.addFileToProject("slides.md", "# Old Title")
        service.rescanSync()
        assertEquals("Old Title", service.activeState()?.title)

        val document = myFixture.openFileInEditor(psiFile.virtualFile).let { myFixture.editor.document }
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText("# New Title")
        }
        service.reloadSync(service.activeState()!!)

        assertEquals("New Title", service.activeState()?.title)
    }

    fun `test markdownFor resolves imported files`() {
        myFixture.addFileToProject("slides.md", "---\nsrc: ./other.md\n---\n")
        val other = myFixture.addFileToProject("other.md", "# Imported").virtualFile

        service.rescanSync()

        assertNotNull(service.markdownFor(other.path))
        assertNotNull(service.stateContaining(other.path))
        assertNull(service.markdownFor("/nowhere.md"))
    }
}
