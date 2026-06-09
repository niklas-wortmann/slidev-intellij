package dev.slidev.intellij.editor

import java.nio.file.Paths

/**
 * Pure logic behind the path completion for frontmatter `src:` import values
 * (https://sli.dev/features/importing-slides): turns the deck's markdown files into
 * insertable src strings — the inverse of [dev.slidev.intellij.parser.SlidevDataLoader.resolveSrcPath].
 * Like [SlidevFrontmatterBlocks], no platform imports, so it is unit-testable.
 */
internal object SlidevSrcPathCompletion {

    /** The typed value with a leading YAML quote stripped; [rootRelative] selects `/`-anchored items. */
    data class Prefix(val matchText: String, val rootRelative: Boolean)

    /** One offered item: the insertable src string and the root-relative path shown as type text. */
    data class Candidate(val text: String, val typeText: String)

    /**
     * Parses the raw value prefix before the caret. Returns null when it contains `#` —
     * the user is typing a page range, not a path. One leading YAML quote is stripped
     * so matching ignores it.
     */
    fun parsePrefix(raw: String): Prefix? {
        if ('#' in raw) {
            return null
        }
        val matchText = if (raw.startsWith("\"") || raw.startsWith("'")) raw.substring(1) else raw
        return Prefix(matchText, matchText.startsWith("/"))
    }

    /** Directories never offering importable files: `node_modules` and dot-directories. */
    fun excludedDirectory(name: String): Boolean = name == "node_modules" || name.startsWith(".")

    /**
     * The offered src values for the absolute markdown [filePaths] of the deck, excluding
     * the importer itself and files outside [root]: root-relative (`/pages/a.md`) when
     * [rootRelative], otherwise relative to the importer's directory (`./pages/a.md`,
     * `../intro.md`). Slash-separated and sorted by text.
     */
    fun candidates(
        filePaths: List<String>,
        importerPath: String,
        root: String,
        rootRelative: Boolean,
    ): List<Candidate> {
        val rootDir = Paths.get(root)
        val importer = Paths.get(importerPath)
        return filePaths.asSequence()
            .map { Paths.get(it) }
            .filter { it != importer && it.startsWith(rootDir) }
            .map { path ->
                val display = slash(rootDir.relativize(path).toString())
                val text = if (rootRelative) {
                    "/$display"
                }
                else {
                    val relative = slash((importer.parent?.relativize(path) ?: path).toString())
                    if (relative.startsWith("../")) relative else "./$relative"
                }
                Candidate(text, display)
            }
            .sortedBy { it.text }
            .toList()
    }

    private fun slash(path: String): String = path.replace('\\', '/')
}
