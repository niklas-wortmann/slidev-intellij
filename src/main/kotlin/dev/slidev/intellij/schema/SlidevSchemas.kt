package dev.slidev.intellij.schema

/**
 * The two vendored schemas of the VS Code language server: `headmatter.json` binds
 * to the first slide's frontmatter, `frontmatter.json` to every other slide's.
 * Regenerated upstream by `scripts/schema.ts` (ts-json-schema-generator over
 * `@slidev/types`); copy from `packages/vscode/schema/` when updating.
 */
object SlidevSchemas {

    val frontmatter: SlidevSchema by lazy { load("/schemas/frontmatter.json") }

    val headmatter: SlidevSchema by lazy { load("/schemas/headmatter.json") }

    fun forSlide(slideIndex: Int): SlidevSchema = if (slideIndex == 0) headmatter else frontmatter

    private fun load(path: String): SlidevSchema {
        val json = SlidevSchemas::class.java.getResourceAsStream(path)
            ?.bufferedReader()?.use { it.readText() }
            ?: return SlidevSchema.parse("{}")
        return SlidevSchema.parse(json)
    }
}
