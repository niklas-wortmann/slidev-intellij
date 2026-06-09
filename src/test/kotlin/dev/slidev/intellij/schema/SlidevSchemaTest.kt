package dev.slidev.intellij.schema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidevSchemaTest {

    @Test
    fun `vendored frontmatter schema resolves properties`() {
        val schema = SlidevSchemas.frontmatter
        assertTrue(schema.properties.isNotEmpty())
        assertNotNull(schema.properties["layout"])
        assertNotNull(schema.properties["transition"])
        assertNotNull(schema.properties["hide"])
        assertNotNull(schema.properties["clicks"])
    }

    @Test
    fun `vendored headmatter schema is a superset with deck options`() {
        val schema = SlidevSchemas.headmatter
        assertNotNull(schema.properties["layout"])
        assertNotNull(schema.properties["theme"])
        assertTrue(schema.properties.size > SlidevSchemas.frontmatter.properties.size)
    }

    @Test
    fun `layout merges builtin enum with a free string branch`() {
        val layout = SlidevSchemas.frontmatter.properties.getValue("layout")
        assertTrue("cover" in layout.enumValues)
        assertTrue("two-cols" in layout.enumValues)
        assertTrue(layout.acceptsAnyString)
        assertTrue(layout.matches("cover"))
        assertTrue(layout.matches("my-custom-layout"))
        assertFalse(layout.matches(42))
    }

    @Test
    fun `transition enumerates builtin transitions and allows objects`() {
        val transition = SlidevSchemas.frontmatter.properties.getValue("transition")
        assertTrue("view-transition" in transition.enumValues)
        assertTrue(transition.matches("fade"))
        assertTrue(transition.matches(null))
        assertTrue(transition.matches(mapOf("appear" to true)))
        assertFalse(transition.matches(1))
    }

    @Test
    fun `boolean and number properties reject other scalars`() {
        val hide = SlidevSchemas.frontmatter.properties.getValue("hide")
        assertTrue(hide.matches(true))
        assertFalse(hide.matches("yes please"))
        assertFalse(hide.matches(1))

        val clicks = SlidevSchemas.frontmatter.properties.getValue("clicks")
        assertTrue(clicks.matches(3))
        assertFalse(clicks.matches("three"))
    }

    @Test
    fun `descriptions carry the schema doc text`() {
        val layout = SlidevSchemas.frontmatter.properties.getValue("layout")
        assertTrue(layout.markdownDescription.orEmpty().contains("Slide layout to use"))
    }

    @Test
    fun `unknown structures degrade to permissive`() {
        val schema = SlidevSchema.parse("""{"definitions": {}}""")
        assertEquals(0, schema.properties.size)
    }
}
