package dev.slidev.intellij.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidevBuiltinComponentsTest {

    @Test
    fun `all documented builtin components are vendored`() {
        val expected = setOf(
            "Arrow", "VDragArrow", "AutoFitText", "LightOrDark", "Link", "PoweredBySlidev",
            "RenderWhen", "SlideCurrentNo", "SlidesTotal", "Toc", "Transform", "Tweet",
            "BlueSky", "VAfter", "VClick", "VClicks", "VSwitch", "VDrag", "SlidevVideo", "Youtube",
        )
        assertEquals(expected, SlidevBuiltinComponents.components.map { it.name }.toSet())
    }

    @Test
    fun `all global directives are vendored`() {
        assertEquals(
            setOf("v-click", "v-after", "v-motion", "v-mark", "v-drag"),
            SlidevBuiltinComponents.directives.map { it.name }.toSet(),
        )
    }

    @Test
    fun `every component has description docs url and builtin origin`() {
        for (component in SlidevBuiltinComponents.components) {
            assertNotNull("${component.name} description", component.description)
            assertTrue("${component.name} docsUrl", component.docsUrl.orEmpty().startsWith("https://sli.dev/"))
            assertEquals(ComponentOrigin.BUILTIN, component.origin)
        }
    }

    @Test
    fun `prop metadata is parsed`() {
        val arrow = SlidevBuiltinComponents.components.first { it.name == "Arrow" }
        val x1 = arrow.prop("x1")
        assertNotNull(x1)
        assertTrue(x1!!.required)
        assertEquals("string | number", x1.type)
        val width = arrow.prop("width")
        assertNotNull(width)
        assertEquals("2", width!!.default)
        assertNotNull(width.description)
    }
}
