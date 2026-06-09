package dev.slidev.intellij.parser

/**
 * One animation step inside a magic-move block (plan.md, 19.2): a nested 3-backtick
 * fence with its own language token and optional Shiki meta — click ranges (`{*|1|2-5}`)
 * and per-step options (`{*}{lines:false}`). [contentStart]/[contentEnd] delimit the code
 * between the fence lines (exclusive of line terminators), as offsets into the scanned
 * text — empty when the step has no content lines.
 */
data class MagicMoveStep(
    val language: String,
    val meta: String,
    val contentStart: Int,
    val contentEnd: Int,
)

/**
 * A [Shiki Magic Move](https://sli.dev/features/shiki-magic-move) block: a 4+-backtick
 * outer fence with info string `md magic-move`, optional options (`{at:4, lines:true}`)
 * and title (`[app.js]`, v0.52+), containing the animation steps. [start]/[end] span the
 * whole block including the fence lines; an unclosed block swallows to the end of text.
 */
data class MagicMoveBlock(
    val start: Int,
    val end: Int,
    val options: String?,
    val title: String?,
    val steps: List<MagicMoveStep>,
)

// Outer opener, on the trimmed line: 4+ backticks, `md`/`markdown` + `magic-move`,
// optional `{options}` then optional `[title]` (the upstream transformer's order).
private val OUTER_FENCE = Regex("^(`{4,})\\s*(?:md|markdown)\\s+magic-move\\s*(\\{[^}]*})?\\s*(?:\\[([^]]*)])?\\s*$")

// Step opener, on the trimmed line: exactly 3 backticks, language token, rest is Shiki meta.
// The token also stops at `{` so the space-less `` ```ts{2,3} `` form keeps its meta separate.
private val STEP_FENCE = Regex("^```(?!`)\\s*([^\\s{]*)\\s*(.*)$")

// A closing fence is backticks only (CommonMark: no info string) — `` ```ts `` mid-step is content.
private val BARE_FENCE = Regex("^(`{3,})\\s*$")

// Any other fence opener (3+ backticks with an info string) — skipped wholesale so a
// magic-move example quoted inside another fence is not picked up.
private val ANY_FENCE = Regex("^(`{3,})")

private data class Line(val start: Int, val text: String) {
    /** Offset just past the content, excluding the line terminator (CRLF-safe). */
    val contentEnd: Int get() = start + text.length
}

private fun splitLines(text: String): List<Line> {
    val lines = mutableListOf<Line>()
    var start = 0
    while (start <= text.length) {
        val nl = text.indexOf('\n', start)
        val end = if (nl < 0) text.length else nl
        val contentEnd = if (end > start && text[end - 1] == '\r') end - 1 else end
        lines.add(Line(start, text.substring(start, contentEnd)))
        if (nl < 0) break
        start = nl + 1
    }
    return lines
}

/**
 * Finds the Shiki Magic Move blocks in [text] (plan.md, 19.2). Pure text scan, sibling of
 * [findCodeBlocks]: fence lines may be indented, non-code text between steps is ignored
 * (upstream treats it as comments), other fenced blocks are skipped so quoted examples
 * don't match, and unclosed fences swallow to the end of the text.
 */
fun findMagicMoveBlocks(text: String): List<MagicMoveBlock> {
    val lines = splitLines(text)
    val blocks = mutableListOf<MagicMoveBlock>()
    var i = 0
    while (i < lines.size) {
        val trimmed = lines[i].text.trimStart()
        val outer = OUTER_FENCE.matchEntire(trimmed)
        if (outer != null) {
            i = scanBlock(lines, i, outer, text.length, blocks)
            continue
        }
        val fence = ANY_FENCE.find(trimmed)
        if (fence != null) {
            // Skip the whole foreign fence, house style (SlidevParser): the closing line
            // repeats at least the opening backtick run; unclosed swallows to EOF.
            val run = fence.groupValues[1]
            var j = i + 1
            while (j < lines.size && !lines[j].text.trimStart().startsWith(run)) j++
            i = j + 1
            continue
        }
        i++
    }
    return blocks
}

/** Scans one block starting at [open]; returns the line index to resume at. */
private fun scanBlock(
    lines: List<Line>,
    open: Int,
    outer: MatchResult,
    textLength: Int,
    blocks: MutableList<MagicMoveBlock>,
): Int {
    val outerRun = outer.groupValues[1]
    val steps = mutableListOf<MagicMoveStep>()
    var end = textLength // unclosed block swallows to EOF
    var i = open + 1
    var resume = lines.size
    while (i < lines.size) {
        val trimmed = lines[i].text.trimStart()
        val bare = BARE_FENCE.matchEntire(trimmed)
        if (bare != null && bare.groupValues[1].length >= outerRun.length) {
            end = lines[i].contentEnd
            resume = i + 1
            break
        }
        val step = STEP_FENCE.matchEntire(trimmed)
        if (step != null) {
            i = scanStep(lines, i, step, outerRun, steps)
            continue
        }
        i++ // non-code text between steps: ignored
    }
    blocks.add(
        MagicMoveBlock(
            start = lines[open].start,
            end = end,
            options = outer.groupValues[2].ifEmpty { null },
            title = outer.groups[3]?.value,
            steps = steps,
        ),
    )
    return resume
}

/** Scans one step starting at [open]; returns the line index of its closing fence. */
private fun scanStep(
    lines: List<Line>,
    open: Int,
    fence: MatchResult,
    outerRun: String,
    steps: MutableList<MagicMoveStep>,
): Int {
    var close = open + 1
    while (close < lines.size) {
        val bare = BARE_FENCE.matchEntire(lines[close].text.trimStart())
        if (bare != null) break // 3 backticks close the step; the outer run is re-checked by the caller
        close++
    }
    // Content spans the lines strictly between the fences; an unclosed step swallows to EOF.
    val contentStart = if (open + 1 < lines.size) lines[open + 1].start else lines[open].contentEnd
    val contentEnd = if (close > open + 1) lines[(close - 1).coerceAtMost(lines.size - 1)].contentEnd else contentStart
    steps.add(
        MagicMoveStep(
            language = fence.groupValues[1],
            meta = fence.groupValues[2].trimEnd(),
            contentStart = contentStart,
            contentEnd = maxOf(contentEnd, contentStart),
        ),
    )
    // A 4+-backtick bare line both ends the step and closes the outer block — hand it back.
    val bare = if (close < lines.size) BARE_FENCE.matchEntire(lines[close].text.trimStart()) else null
    return if (bare != null && bare.groupValues[1].length >= outerRun.length) close else close + 1
}
