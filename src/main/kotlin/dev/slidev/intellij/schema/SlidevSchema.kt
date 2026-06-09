package dev.slidev.intellij.schema

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Minimal reader for the vendored Slidev JSON schemas (`schemas/frontmatter.json`,
 * `schemas/headmatter.json`), the counterpart of the schema binding in the VS Code
 * language server. Only the subset the generated schemas actually use is supported:
 * a root `$ref` into `definitions`, per-property `type` (string or list), `anyOf`,
 * `$ref`, and `enum`. The JSON is loaded with the bundled SnakeYAML (JSON is YAML).
 * This package must stay free of IntelliJ Platform imports.
 */
class SlidevSchema private constructor(val properties: Map<String, SchemaProperty>) {

    companion object {

        fun parse(json: String): SlidevSchema {
            @Suppress("UNCHECKED_CAST")
            val root = Yaml(SafeConstructor(LoaderOptions())).load<Any?>(json) as? Map<String, Any?>
                ?: return SlidevSchema(emptyMap())
            val definitions = root["definitions"].asMap()
            val rootDef = resolve(root, definitions)
            val properties = rootDef["properties"].asMap().entries.associate { (name, prop) ->
                name to SchemaProperty.from(name, prop.asMap(), definitions)
            }
            return SlidevSchema(properties)
        }

        private fun resolve(schema: Map<String, Any?>, definitions: Map<String, Any?>): Map<String, Any?> {
            val ref = schema["\$ref"] as? String ?: return schema
            val name = ref.substringAfterLast('/')
            return definitions[name].asMap()
        }

        @Suppress("UNCHECKED_CAST")
        internal fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

        @Suppress("UNCHECKED_CAST")
        internal fun Any?.asList(): List<Any?> = this as? List<Any?> ?: emptyList()
    }
}

/**
 * One top-level frontmatter property, flattened across `anyOf`/`$ref` branches:
 * [types] is the union of accepted JSON types, [enumValues] the union of enum
 * constants. A string value outside [enumValues] is only invalid when the schema
 * has no plain-string branch ([acceptsAnyString] false).
 */
class SchemaProperty(
    val name: String,
    val description: String?,
    val markdownDescription: String?,
    val types: Set<String>,
    val enumValues: List<String>,
) {
    val acceptsAnyString: Boolean get() = "string" in types

    /** Short type text for completion items, e.g. `boolean` or `fade | slide-up | …`. */
    val typeText: String
        get() = when {
            enumValues.isNotEmpty() && !acceptsAnyString -> enumValues.joinToString(" | ")
            enumValues.isNotEmpty() -> "string"
            else -> types.sorted().joinToString(" | ").ifEmpty { "any" }
        }

    /** JSON-schema validation of a SnakeYAML-loaded scalar/collection value. */
    fun matches(value: Any?): Boolean = when {
        types.isEmpty() && enumValues.isEmpty() -> true
        value == null -> "null" in types
        value is Boolean -> "boolean" in types
        value is Number -> "number" in types || "integer" in types
        value is String -> acceptsAnyString || value in enumValues
        value is Map<*, *> -> "object" in types
        value is List<*> -> "array" in types
        else -> true
    }

    /** Human-readable expectation for validation messages. */
    val expectation: String
        get() = if (enumValues.isNotEmpty() && !acceptsAnyString) {
            "one of " + enumValues.joinToString(", ")
        }
        else {
            types.sorted().joinToString(" or ")
        }

    companion object {
        internal fun from(name: String, schema: Map<String, Any?>, definitions: Map<String, Any?>): SchemaProperty {
            val types = mutableSetOf<String>()
            val enums = mutableListOf<String>()
            collect(schema, definitions, types, enums)
            return SchemaProperty(
                name = name,
                description = schema["description"] as? String,
                markdownDescription = schema["markdownDescription"] as? String,
                types = types,
                enumValues = enums,
            )
        }

        private fun collect(
            schema: Map<String, Any?>,
            definitions: Map<String, Any?>,
            types: MutableSet<String>,
            enums: MutableList<String>,
        ) {
            val ref = schema["\$ref"] as? String
            if (ref != null) {
                collect(SlidevSchema.run { definitions[ref.substringAfterLast('/')].asMap() }, definitions, types, enums)
                return
            }
            for (branch in SlidevSchema.run { schema["anyOf"].asList() }) {
                collect(SlidevSchema.run { branch.asMap() }, definitions, types, enums)
            }
            val enum = SlidevSchema.run { schema["enum"].asList() }
            if (enum.isNotEmpty()) {
                enums.addAll(enum.map { it.toString() })
            }
            else {
                when (val type = schema["type"]) {
                    is String -> types.add(type)
                    is List<*> -> type.forEach { types.add(it.toString()) }
                }
            }
        }
    }
}
