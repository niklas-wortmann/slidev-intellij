package dev.slidev.intellij.components

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * The vendored metadata of Slidev's built-in components and global directives
 * (`components/builtin-components.json`), the index counterpart of the vendored
 * frontmatter schemas. Hand-vendored from the Slidev repo's
 * `.vue` prop definitions under `packages/client/builtin` and `docs/builtin/components.md`;
 * re-check both when updating. Loaded with the bundled SnakeYAML (JSON is YAML),
 * like [dev.slidev.intellij.schema.SlidevSchemas].
 */
object SlidevBuiltinComponents {

    val components: List<SlidevComponent> by lazy { parsed.first }

    val directives: List<SlidevDirective> by lazy { parsed.second }

    private val parsed: Pair<List<SlidevComponent>, List<SlidevDirective>> by lazy { load() }

    private fun load(): Pair<List<SlidevComponent>, List<SlidevDirective>> {
        val json = SlidevBuiltinComponents::class.java.getResourceAsStream("/components/builtin-components.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: return emptyList<SlidevComponent>() to emptyList()
        val root = Yaml(SafeConstructor(LoaderOptions())).load<Any?>(json).asMap()
        val components = root["components"].asList().map { entry ->
            val map = entry.asMap()
            SlidevComponent(
                name = map["name"].toString(),
                description = map["description"] as? String,
                docsUrl = map["docsUrl"] as? String,
                props = map["props"].asList().map { prop ->
                    val p = prop.asMap()
                    SlidevComponentProp(
                        name = p["name"].toString(),
                        type = p["type"] as? String,
                        required = p["required"] == true,
                        default = p["default"] as? String,
                        description = p["description"] as? String,
                    )
                },
                origin = ComponentOrigin.BUILTIN,
            )
        }
        val directives = root["directives"].asList().map { entry ->
            val map = entry.asMap()
            SlidevDirective(
                name = map["name"].toString(),
                description = map["description"] as? String,
                docsUrl = map["docsUrl"] as? String,
            )
        }
        return components to directives
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asList(): List<Any?> = this as? List<Any?> ?: emptyList()
}
