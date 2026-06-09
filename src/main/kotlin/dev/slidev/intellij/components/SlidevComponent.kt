package dev.slidev.intellij.components

/**
 * Data model of the component index (plan.md, Phase 13): Vue components usable in slide
 * content, mirroring the resolution of `unplugin-vue-components` in Slidev — built-ins,
 * the local `components/` directory, and theme/addon packages.
 * This package must stay free of IntelliJ Platform imports.
 */

/** Where a component comes from; later origins in declaration order shadow earlier ones. */
enum class ComponentOrigin { BUILTIN, THEME, ADDON, LOCAL }

/** One prop of a component, vendored (built-ins) or extracted from `defineProps` (scanned). */
class SlidevComponentProp(
    val name: String,
    val type: String?,
    val required: Boolean = false,
    val default: String? = null,
    val description: String? = null,
) {
    /** Short type text for completion items. */
    val typeText: String get() = type ?: "any"
}

/** One component usable as a tag in slide content. */
class SlidevComponent(
    val name: String,
    val description: String?,
    val docsUrl: String?,
    val props: List<SlidevComponentProp>,
    val origin: ComponentOrigin,
    /** Absolute path of the defining `.vue` file; null for built-ins. */
    val filePath: String? = null,
) {
    fun prop(name: String): SlidevComponentProp? = props.firstOrNull { it.name == name }
}

/** A global Slidev template directive (`v-click`, `v-mark`, ...). */
class SlidevDirective(
    val name: String,
    val description: String?,
    val docsUrl: String?,
)
