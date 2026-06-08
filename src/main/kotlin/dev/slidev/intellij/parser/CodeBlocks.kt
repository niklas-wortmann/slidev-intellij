package dev.slidev.intellij.parser

/**
 * A fenced code block found by [findCodeBlocks]. Following the upstream semantics of
 * `findCodeBlocks` in `annotations.ts` of the VS Code extension, [startLine] is the
 * first line *inside* the block (0-based), [endLine] is the closing fence line, so the
 * numbered lines are `startLine until endLine`, and [indent] is the fence indentation.
 */
data class CodeBlock(
    val startLine: Int,
    val endLine: Int,
    val indent: Int,
)

private val LEADING_BACKTICKS = Regex("^\\s*`+")

/**
 * Finds the fenced code blocks whose lines should get virtual line numbers.
 * Kotlin port of `findCodeBlocks` in `annotations.ts`: only exactly-three-backtick
 * fences open a block, the closing fence must repeat the same indentation and
 * backticks, and unclosed blocks are ignored.
 */
fun findCodeBlocks(lines: List<String>): List<CodeBlock> {
    val blocks = mutableListOf<CodeBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            val indent = line.length - trimmed.length
            val fence = LEADING_BACKTICKS.find(line)!!.value
            val backtickCount = fence.trim().length
            if (backtickCount != 3) {
                i++
                continue
            }
            val startLine = i
            var endLine = i
            for (j in i + 1 until lines.size) {
                if (lines[j].startsWith(fence)) {
                    endLine = j
                    break
                }
            }
            if (endLine > startLine) {
                blocks.add(CodeBlock(startLine + 1, endLine, indent))
            }
            i = endLine
        }
        i++
    }
    return blocks
}
