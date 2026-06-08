package dev.slidev.intellij.core

import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths

/**
 * Include/exclude glob matching for project-relative paths, mirroring the
 * `slidev.include` / `slidev.exclude` settings of the VS Code extension.
 * A `**` in java.nio globs requires at least one segment, so globs starting
 * with `**` and a slash are widened to also match at the top level
 * (`slides.md` as well as `docs/slides.md`).
 */
class SlidevGlobs(include: List<String>, exclude: String?) {
    private val includeMatchers = include.flatMap(::compile)
    private val excludeMatchers = exclude?.let(::compile) ?: emptyList()

    /** Matches a `/`-separated path relative to the content root (no leading slash). */
    fun matches(relativePath: String): Boolean {
        val path = Paths.get(relativePath.trim('/'))
        return includeMatchers.any { it.matches(path) } && excludeMatchers.none { it.matches(path) }
    }

    private fun compile(glob: String): List<PathMatcher> {
        val pattern = glob.trim().removePrefix("./")
        if (pattern.isEmpty()) {
            return emptyList()
        }
        val fs = FileSystems.getDefault()
        return buildList {
            add(fs.getPathMatcher("glob:$pattern"))
            if (pattern.startsWith("**/")) {
                add(fs.getPathMatcher("glob:${pattern.removePrefix("**/")}"))
            }
        }
    }
}
