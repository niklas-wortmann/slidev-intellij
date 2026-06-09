package dev.slidev.intellij.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SlidevSettingsTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            // The light test project is reused between test classes; restore the defaults
            // so glob-dependent tests elsewhere keep finding their fixtures.
            SlidevSettings.getInstance(project).loadState(SlidevSettings.State())
            SlidevWorkspaceState.getInstance(project).loadState(SlidevWorkspaceState.State())
        }
        finally {
            super.tearDown()
        }
    }

    fun `test defaults match the VS Code extension`() {
        val state = SlidevSettings.getInstance(project).state
        assertEquals(3030, state.port)
        assertTrue(state.annotations)
        assertTrue(state.previewSync)
        assertEquals(listOf("**/slides.md"), state.include)
        assertEquals("**/node_modules/**", state.exclude)
        assertEquals("npm exec -c 'slidev \${args}'", state.devCommand)
    }

    fun `test settings state round-trips through loadState`() {
        val settings = SlidevSettings.getInstance(project)
        val loaded = SlidevSettings.State().apply {
            port = 4040
            annotations = false
            include = mutableListOf("**/deck.md", "talks/*.md")
            exclude = "**/dist/**"
            devCommand = "pnpm slidev \${args} --port \${port}"
        }
        settings.loadState(loaded)

        val state = settings.state
        assertEquals(4040, state.port)
        assertFalse(state.annotations)
        assertEquals(listOf("**/deck.md", "talks/*.md"), state.include)
        assertEquals("**/dist/**", state.exclude)
        assertEquals("pnpm slidev \${args} --port \${port}", state.devCommand)
    }

    fun `test workspace state round-trips through loadState`() {
        val workspace = SlidevWorkspaceState.getInstance(project)
        // The light test project is reused between test classes; start from a clean state.
        workspace.loadState(SlidevWorkspaceState.State())
        assertEmpty(workspace.state.entries)
        assertNull(workspace.state.activeEntry)

        workspace.loadState(
            SlidevWorkspaceState.State().apply {
                entries = mutableListOf("/proj/slides.md", "/proj/docs/slides.md")
                activeEntry = "/proj/slides.md"
            },
        )
        assertEquals(listOf("/proj/slides.md", "/proj/docs/slides.md"), workspace.state.entries)
        assertEquals("/proj/slides.md", workspace.state.activeEntry)
    }
}
