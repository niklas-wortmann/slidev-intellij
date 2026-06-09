package dev.slidev.intellij.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VueComponentScannerTest {

    // ---------------------------------------------------------------- names

    @Test
    fun `component name from file name mirrors unplugin-vue-components`() {
        assertEquals("MyComponent", VueComponentScanner.componentName("MyComponent.vue"))
        assertEquals("MyComponent", VueComponentScanner.componentName("my-component.vue"))
        assertEquals("FooBar", VueComponentScanner.componentName("foo_bar.vue"))
        assertEquals("FooBarBaz", VueComponentScanner.componentName("foo-bar.baz.vue"))
        assertEquals("Counter", VueComponentScanner.componentName("counter.vue"))
    }

    // ---------------------------------------------------------------- type-literal form

    @Test
    fun `type literal props with optionality`() {
        val props = VueComponentScanner.props(
            """
            <script setup lang="ts">
            const props = defineProps<{
              pos?: string
              label: string
            }>()
            </script>
            """.trimIndent(),
        )
        assertEquals(listOf("pos", "label"), props.map { it.name })
        assertFalse(props[0].required)
        assertEquals("string", props[0].type)
        assertTrue(props[1].required)
    }

    @Test
    fun `type literal inside withDefaults`() {
        val props = VueComponentScanner.props(
            """
            const props = withDefaults(defineProps<{ max?: number; min?: number }>(), { max: 100, min: 30 })
            """.trimIndent(),
        )
        assertEquals(listOf("max", "min"), props.map { it.name })
        assertEquals("number", props[0].type)
    }

    @Test
    fun `type literal honors nested object types generics and function types`() {
        val props = VueComponentScanner.props(
            """
            defineProps<{
              items: Array<{ a: number, b: string }>
              handler?: (x: number) => void
              nested: { x: number; y: number }
              readonly mode?: 'a' | 'b'
            }>()
            """.trimIndent(),
        )
        assertEquals(listOf("items", "handler", "nested", "mode"), props.map { it.name })
        assertEquals("Array<{ a: number, b: string }>", props[0].type)
        assertEquals("(x: number) => void", props[1].type)
        assertFalse(props[3].required)
    }

    @Test
    fun `type literal skips strings and comments`() {
        val props = VueComponentScanner.props(
            """
            defineProps<{
              // separator?: string is just a comment
              label: 'a;b' | "c,d"
              /* block, with: separators */
              value?: number
            }>()
            """.trimIndent(),
        )
        assertEquals(listOf("label", "value"), props.map { it.name })
    }

    // ---------------------------------------------------------------- object form

    @Test
    fun `object literal props with required detection`() {
        val props = VueComponentScanner.props(
            """
            defineProps({
              count: { type: Number, required: true },
              label: String,
              'quoted-name': {},
            })
            """.trimIndent(),
        )
        assertEquals(listOf("count", "label", "quoted-name"), props.map { it.name })
        assertTrue(props[0].required)
        assertFalse(props[1].required)
        assertFalse(props[2].required)
    }

    @Test
    fun `object literal honors nested defaults and functions`() {
        val props = VueComponentScanner.props(
            """
            defineProps({
              config: { type: Object, default: () => ({ a: 1, b: 2 }) },
              fmt: { type: Function, default: (v) => v + ',' },
            })
            """.trimIndent(),
        )
        assertEquals(listOf("config", "fmt"), props.map { it.name })
    }

    // ---------------------------------------------------------------- edges

    @Test
    fun `no defineProps yields no props`() {
        assertTrue(VueComponentScanner.props("<template><div/></template>").isEmpty())
        assertTrue(VueComponentScanner.props("").isEmpty())
    }

    @Test
    fun `scan combines name and props`() {
        val component = VueComponentScanner.scan("item-card.vue", "defineProps<{ title: string }>()")
        assertEquals("ItemCard", component.name)
        assertEquals(listOf("title"), component.props.map { it.name })
    }
}
