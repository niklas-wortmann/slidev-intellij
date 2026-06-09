package dev.slidev.intellij.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeBlocksTest {

    private fun blocks(text: String) = findCodeBlocks(text.lines())

    @Test
    fun `simple fenced block reports interior lines`() {
        val found = blocks("# Title\n```ts\nconst a = 1\nconst b = 2\n```\ntext")
        assertEquals(listOf(CodeBlock(startLine = 2, endLine = 4, indent = 0)), found)
    }

    @Test
    fun `empty block has no interior lines`() {
        // Upstream still reports the block; `startLine == endLine` means nothing gets numbered.
        assertEquals(listOf(CodeBlock(startLine = 1, endLine = 1, indent = 0)), blocks("```\n```"))
    }

    @Test
    fun `unclosed block is skipped`() {
        assertEquals(emptyList<CodeBlock>(), blocks("```ts\nconst a = 1"))
    }

    @Test
    fun `four-backtick fences do not open blocks but nested three-backtick ones do`() {
        // Matches the upstream behavior: the ````md fence itself is skipped, the
        // nested ```ts block still gets line numbers.
        val found = blocks("````md\n```ts\ncode\n```\n````\n```js\nlet x\n```")
        assertEquals(
            listOf(
                CodeBlock(startLine = 2, endLine = 3, indent = 0),
                CodeBlock(startLine = 6, endLine = 7, indent = 0),
            ),
            found,
        )
    }

    @Test
    fun `magic-move steps get line numbers but the outer fence does not`() {
        // 19.6 regression pin: only the inner 3-backtick steps are numbered, matching
        // the VS Code annotator; the 4-backtick magic-move fence itself is skipped.
        val found = blocks("````md magic-move {at:4} [app.js]\n```js {*|2}\nconst a = 1\n```\n```ts\nlet b\n```\n````")
        assertEquals(
            listOf(
                CodeBlock(startLine = 2, endLine = 3, indent = 0),
                CodeBlock(startLine = 5, endLine = 6, indent = 0),
            ),
            found,
        )
    }

    @Test
    fun `indented fences keep their indentation`() {
        val found = blocks("- item\n  ```ts\n  code\n  ```")
        assertEquals(listOf(CodeBlock(startLine = 2, endLine = 3, indent = 2)), found)
    }

    @Test
    fun `multiple blocks are all found`() {
        val found = blocks("```a\n1\n```\n\n```b\n2\n3\n```")
        assertEquals(
            listOf(
                CodeBlock(startLine = 1, endLine = 2, indent = 0),
                CodeBlock(startLine = 5, endLine = 7, indent = 0),
            ),
            found,
        )
    }
}
