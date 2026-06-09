package dev.slidev.intellij.components

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.slidev.intellij.project.SlidevProjectService

class SlidevComponentIndexTest : BasePlatformTestCase() {

    private val service: SlidevProjectService
        get() = SlidevProjectService.getInstance(project)

    private val index: SlidevComponentIndex
        get() = SlidevComponentIndex.getInstance(project)

    private fun activeEntry(): String {
        service.rescanSync()
        return service.activeState()!!.entryPath
    }

    fun `test builtins only for paths outside any slidev project`() {
        val components = index.componentsFor("/nowhere/slides.md")
        assertTrue(components.containsKey("Arrow"))
        assertEquals(ComponentOrigin.BUILTIN, components.getValue("Toc").origin)
    }

    fun `test local components dir is scanned with props`() {
        myFixture.addFileToProject("slides.md", "# Deck")
        myFixture.addFileToProject(
            "components/Counter.vue",
            "<script setup>const props = defineProps({ count: { type: Number, required: true } })</script>",
        )
        myFixture.addFileToProject("components/nested/item-card.vue", "<template><div/></template>")

        val components = index.componentsFor(activeEntry())

        val counter = components.getValue("Counter")
        assertEquals(ComponentOrigin.LOCAL, counter.origin)
        assertEquals(listOf("count"), counter.props.map { it.name })
        assertTrue(counter.props[0].required)
        assertNotNull(counter.filePath)
        // nested dirs and kebab-case file names are picked up
        assertEquals(ComponentOrigin.LOCAL, components.getValue("ItemCard").origin)
    }

    fun `test local component shadows a builtin of the same name`() {
        myFixture.addFileToProject("slides.md", "# Deck")
        myFixture.addFileToProject("components/Toc.vue", "<template><div/></template>")

        val components = index.componentsFor(activeEntry())

        assertEquals(ComponentOrigin.LOCAL, components.getValue("Toc").origin)
        assertEquals(ComponentOrigin.BUILTIN, components.getValue("Arrow").origin)
    }

    fun `test theme and addon components resolved from node_modules`() {
        myFixture.addFileToProject("slides.md", "---\ntheme: seriph\naddons:\n  - excalidraw\n---\n\n# Deck")
        myFixture.addFileToProject("node_modules/@slidev/theme-seriph/components/SeriphThing.vue", "<template/>")
        myFixture.addFileToProject("node_modules/slidev-addon-excalidraw/components/Excalidraw.vue", "<template/>")

        val components = index.componentsFor(activeEntry())

        assertEquals(ComponentOrigin.THEME, components.getValue("SeriphThing").origin)
        assertEquals(ComponentOrigin.ADDON, components.getValue("Excalidraw").origin)
    }

    fun `test local component shadows theme component of the same name`() {
        myFixture.addFileToProject("slides.md", "---\ntheme: seriph\n---\n\n# Deck")
        myFixture.addFileToProject("node_modules/@slidev/theme-seriph/components/Brand.vue", "<template/>")
        myFixture.addFileToProject("components/Brand.vue", "<template/>")

        assertEquals(ComponentOrigin.LOCAL, index.componentsFor(activeEntry()).getValue("Brand").origin)
    }

    fun `test new vue file invalidates the cache`() {
        myFixture.addFileToProject("slides.md", "# Deck")
        val entry = activeEntry()
        assertFalse(index.componentsFor(entry).containsKey("Late"))

        myFixture.addFileToProject("components/Late.vue", "<template/>")

        assertTrue(index.componentsFor(entry).containsKey("Late"))
    }

    fun `test directives come from the vendored metadata`() {
        assertEquals(
            setOf("v-click", "v-after", "v-motion", "v-mark", "v-drag"),
            index.directives().map { it.name }.toSet(),
        )
        assertNotNull(index.directive("v-click"))
        assertNull(index.directive("v-unknown"))
    }
}
