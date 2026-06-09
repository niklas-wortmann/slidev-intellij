package dev.slidev.intellij.components

/**
 * Maps headmatter `theme:` / `addons:` names to `node_modules` package-name candidates,
 * mirroring `resolveThemeName` / `resolveAddons` in `packages/slidev/node/resolver.ts`:
 * official packages are `@slidev/theme-*`, community ones `slidev-theme-*` /
 * `slidev-addon-*`; already-prefixed and scoped names pass through, `.`/`/`-prefixed
 * names are local paths. This package must stay free of IntelliJ Platform imports.
 */
object SlidevPackageNames {

    /** True when [name] addresses a local directory instead of an installed package. */
    fun isLocalPath(name: String): Boolean = name.startsWith(".") || name.startsWith("/")

    /** Package-name candidates for headmatter `theme:` [name], in probing order. */
    fun themeCandidates(name: String?): List<String> {
        // Slidev defaults to the `default` theme when the headmatter has none.
        val theme = name?.trim().takeUnless { it.isNullOrEmpty() } ?: "default"
        return when {
            theme == "none" || isLocalPath(theme) -> emptyList()
            theme.startsWith("@slidev/theme-") || theme.startsWith("slidev-theme-") -> listOf(theme)
            theme.startsWith("@") -> listOf(theme)
            else -> listOf("@slidev/theme-$theme", "slidev-theme-$theme")
        }
    }

    /** Package-name candidates for one headmatter `addons:` entry, in probing order. */
    fun addonCandidates(name: String): List<String> {
        val addon = name.trim()
        return when {
            addon.isEmpty() || isLocalPath(addon) -> emptyList()
            addon.startsWith("slidev-addon-") || addon.startsWith("@") -> listOf(addon)
            else -> listOf("slidev-addon-$addon", addon)
        }
    }
}
