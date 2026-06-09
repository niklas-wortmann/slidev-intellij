package dev.slidev.intellij.generator

/** Package managers offered when scaffolding a new project, like `create-slidev`'s agent prompt. */
enum class PackageManager(val displayName: String) {
    NPM("npm"),
    PNPM("pnpm"),
    YARN("yarn"),
    BUN("bun");

    /** The install command run after scaffolding; `<pm> install` works for all four. */
    val installCommand: String get() = "$displayName install"

    override fun toString(): String = displayName
}

/** Options gathered by [SlidevGeneratorPeer] before [SlidevProjectGenerator] scaffolds the project. */
data class SlidevGeneratorSettings(
    val packageManager: PackageManager = PackageManager.NPM,
    val installDependencies: Boolean = true,
)
