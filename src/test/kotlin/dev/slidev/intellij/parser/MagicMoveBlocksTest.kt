package dev.slidev.intellij.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MagicMoveBlocksTest {

    private fun String.contentOf(step: MagicMoveStep) = substring(step.contentStart, step.contentEnd)

    @Test
    fun `basic block reports steps with languages and content ranges`() {
        val text = "````md magic-move\n```js\nconst a = 1\n```\n```ts\nconst b = 2\n```\n````\n"
        val blocks = findMagicMoveBlocks(text)
        assertEquals(1, blocks.size)
        val block = blocks.single()
        assertEquals(0, block.start)
        assertEquals(text.indexOf("\n````") + "\n````".length, block.end)
        assertNull(block.options)
        assertNull(block.title)
        assertEquals(listOf("js", "ts"), block.steps.map { it.language })
        assertEquals("const a = 1", text.contentOf(block.steps[0]))
        assertEquals("const b = 2", text.contentOf(block.steps[1]))
    }

    @Test
    fun `options and title are parsed from the outer fence`() {
        val text = "````md magic-move {at:4, lines:true} [app.js]\n```js\nx\n```\n````\n"
        val block = findMagicMoveBlocks(text).single()
        assertEquals("{at:4, lines:true}", block.options)
        assertEquals("app.js", block.title)
    }

    @Test
    fun `options only and title only both parse`() {
        val options = findMagicMoveBlocks("````md magic-move {at:2}\n````\n").single()
        assertEquals("{at:2}", options.options)
        assertNull(options.title)

        val title = findMagicMoveBlocks("````md magic-move [demo.ts]\n````\n").single()
        assertNull(title.options)
        assertEquals("demo.ts", title.title)
    }

    @Test
    fun `markdown alias is recognized`() {
        assertEquals(1, findMagicMoveBlocks("````markdown magic-move\n````\n").size)
    }

    @Test
    fun `step meta is split off the language token`() {
        val text = "````md magic-move\n```js {*|2|5-6}\na\n```\n```ts {*}{lines:false}\nb\n```\n```ts{2,3}\nc\n```\n````\n"
        val steps = findMagicMoveBlocks(text).single().steps
        assertEquals(listOf("js", "ts", "ts"), steps.map { it.language })
        assertEquals(listOf("{*|2|5-6}", "{*}{lines:false}", "{2,3}"), steps.map { it.meta })
    }

    @Test
    fun `non-code text between steps is ignored`() {
        val text = "````md magic-move\nThis is a comment\n```js\na\n```\nmore comments\n```ts\nb\n```\n````\n"
        val steps = findMagicMoveBlocks(text).single().steps
        assertEquals(listOf("js", "ts"), steps.map { it.language })
        assertEquals("a", text.contentOf(steps[0]))
        assertEquals("b", text.contentOf(steps[1]))
    }

    @Test
    fun `unclosed outer fence swallows to end of text`() {
        val text = "````md magic-move\n```js\na\n```\ntrailing"
        val block = findMagicMoveBlocks(text).single()
        assertEquals(text.length, block.end)
        assertEquals("a", text.contentOf(block.steps.single()))
    }

    @Test
    fun `unclosed step ends at the outer closing fence`() {
        val text = "````md magic-move\n```js\nconst a = 1\n````\nafter"
        val block = findMagicMoveBlocks(text).single()
        assertEquals(text.indexOf("\nafter"), block.end)
        assertEquals("const a = 1", text.contentOf(block.steps.single()))
    }

    @Test
    fun `multi-line step content spans all lines`() {
        val text = "````md magic-move\n```js\nline 1\nline 2\n```\n````\n"
        assertEquals("line 1\nline 2", text.contentOf(findMagicMoveBlocks(text).single().steps.single()))
    }

    @Test
    fun `empty step has an empty content range`() {
        val step = findMagicMoveBlocks("````md magic-move\n```js\n```\n````\n").single().steps.single()
        assertEquals(step.contentStart, step.contentEnd)
    }

    @Test
    fun `crlf content ranges exclude the carriage return`() {
        val text = "````md magic-move\r\n```js\r\nconst a = 1\r\n```\r\n````\r\n"
        val block = findMagicMoveBlocks(text).single()
        assertEquals("const a = 1", text.contentOf(block.steps.single()))
    }

    @Test
    fun `a step closing fence with an info string is content, not a close`() {
        // CommonMark: a closing fence may not carry an info string — ```ts mid-step is literal.
        val text = "````md magic-move\n```js\n a\n```ts\n b\n```\n````\n"
        val step = findMagicMoveBlocks(text).single().steps.single()
        assertEquals(" a\n```ts\n b", text.contentOf(step))
    }

    @Test
    fun `magic-move example quoted inside another fence is not picked up`() {
        val text = "`````txt\n````md magic-move\n```js\na\n```\n````\n`````\n"
        assertEquals(emptyList<MagicMoveBlock>(), findMagicMoveBlocks(text))
    }

    @Test
    fun `regular fences elsewhere are skipped`() {
        val text = "```js\nconst x = 1\n```\n\n````md magic-move\n```ts\nb\n```\n````\n"
        val blocks = findMagicMoveBlocks(text)
        assertEquals(1, blocks.size)
        assertEquals("ts", blocks.single().steps.single().language)
    }

    @Test
    fun `two blocks in one document are both found`() {
        val text = "````md magic-move\n```js\na\n```\n````\n\ntext\n\n````md magic-move\n```ts\nb\n```\n````\n"
        val blocks = findMagicMoveBlocks(text)
        assertEquals(2, blocks.size)
        assertEquals("js", blocks[0].steps.single().language)
        assertEquals("ts", blocks[1].steps.single().language)
    }

    @Test
    fun `indented fences are recognized`() {
        val text = "  ````md magic-move\n  ```js\n  const a = 1\n  ```\n  ````\n"
        val block = findMagicMoveBlocks(text).single()
        assertEquals("  const a = 1", text.contentOf(block.steps.single()))
    }

    @Test
    fun `plain fence with the md language alone is not magic-move`() {
        assertEquals(emptyList<MagicMoveBlock>(), findMagicMoveBlocks("````md\ncontent\n````\n"))
    }
}
