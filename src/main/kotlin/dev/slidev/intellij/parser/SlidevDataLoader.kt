package dev.slidev.intellij.parser

import java.nio.file.Paths

/**
 * Provides the text of a markdown file, or null when it does not exist.
 * Injected so the parser package stays platform-free: the plugin passes a provider
 * backed by open Documents (unsaved edits) with a VFS fallback; tests pass a map.
 */
fun interface FileTextProvider {
    fun text(path: String): String?
}

/**
 * Port of `load` from `@slidev/parser/fs`: parses the entry file and recursively
 * resolves `src:` frontmatter imports (with optional `#1-3` range suffixes) into a
 * flattened slide list. Preparser extensions and feature detection are not ported.
 */
object SlidevDataLoader {
    fun load(userRoot: String, filepath: String, provider: FileTextProvider): LoadedSlidevData =
        Loader(userRoot, provider).load(filepath)

    /**
     * Resolves a frontmatter `src` value (an optional `#1-3` range suffix is dropped) the
     * way [Loader.loadSlide] does: a leading `/` is relative to [userRoot], anything else
     * to the directory of [importerPath]. Returns the normalized, slash-separated path.
     */
    fun resolveSrcPath(src: String, importerPath: String, userRoot: String): String {
        val rawPath = src.substringBefore('#')
        return slash(
            if (rawPath.startsWith("/")) {
                Paths.get(userRoot).resolve(rawPath.substring(1)).normalize().toString()
            }
            else {
                val parent = Paths.get(importerPath).parent
                (parent?.resolve(rawPath) ?: Paths.get(rawPath)).normalize().toString()
            },
        )
    }

    private class Loader(private val userRoot: String, private val provider: FileTextProvider) {
        private val markdownFiles = LinkedHashMap<String, SlidevMarkdown>()
        private val errors = LinkedHashMap<String, MutableList<SlideError>>()
        private val slides = mutableListOf<ResolvedSlide>()

        fun load(filepath: String): LoadedSlidevData {
            val entry = loadMarkdown(slash(filepath), range = null, frontmatterOverride = null, importers = null)

            val headmatter = LinkedHashMap<String, Any?>(entry.slides.firstOrNull()?.frontmatter ?: emptyMap())
            val firstTitle = slides.firstOrNull()?.title
            if (firstTitle != null && headmatter["title"] == null) {
                headmatter["title"] = firstTitle
            }

            return LoadedSlidevData(
                slides = slides,
                entry = entry,
                headmatter = headmatter,
                markdownFiles = markdownFiles,
                errors = errors,
            )
        }

        private fun addError(path: String, row: Int, message: String) {
            errors.getOrPut(path) { mutableListOf() }.add(SlideError(row, message))
        }

        private fun loadMarkdown(
            path: String,
            range: String?,
            frontmatterOverride: Map<String, Any?>?,
            importers: List<SourceSlide>?,
        ): SlidevMarkdown {
            val md = markdownFiles.getOrPut(path) {
                SlidevParser.parse(provider.text(path) ?: "", path)
            }

            for (index in RangeParser.parseRangeString(md.slides.size, range)) {
                val subSlide = md.slides.getOrNull(index - 1) ?: continue
                loadSlide(subSlide, frontmatterOverride, importers)
            }

            return md
        }

        private fun loadSlide(
            slide: SourceSlide,
            frontmatterOverride: Map<String, Any?>?,
            importChain: List<SourceSlide>?,
        ) {
            if (slide.isHidden) {
                return
            }
            val src = slide.frontmatter["src"]
            if (src is String && src.isNotEmpty()) {
                // Not in upstream: guard against mutually-importing files (upstream overflows the stack).
                if (importChain?.any { it.filepath == slide.filepath && it.index == slide.index } == true) {
                    addError(slide.filepath, slide.start, "Circular import detected: ${slide.filepath}#${slide.index + 1}")
                    return
                }

                val rangeRaw = src.split('#').getOrNull(1)
                val path = resolveSrcPath(src, slide.filepath, userRoot)

                val override = (slide.frontmatter + (frontmatterOverride ?: emptyMap())).toMutableMap()
                override.remove("src")

                if (provider.text(path) == null) {
                    addError(slide.filepath, slide.start, "Imported markdown file not found: $path")
                }
                else {
                    loadMarkdown(path, rangeRaw, override, (importChain ?: emptyList()) + slide)
                }
            }
            else {
                slides.add(
                    ResolvedSlide(
                        frontmatter = slide.frontmatter + (frontmatterOverride ?: emptyMap()),
                        content = slide.content,
                        note = slide.note,
                        title = slide.title,
                        level = slide.level,
                        index = slides.size,
                        importChain = importChain,
                        source = slide,
                    ),
                )
            }
        }
    }

    private fun slash(path: String): String = path.replace('\\', '/')
}
