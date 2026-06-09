package dev.slidev.intellij.generator

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes the bundled starter template (a copy of `create-slidev`'s `template/` directory) into a
 * target directory, applying the same transformations `create-slidev` performs on scaffold:
 * renaming `_gitignore`, substituting the package name, and adjusting the README commands to the
 * chosen package manager.
 */
object SlidevStarterTemplate {

    private const val RESOURCE_ROOT = "/slidev/template"

    /** Bundled template files; explicit because jar directories cannot be listed via the classloader. */
    val FILES: List<String> = listOf(
        "slides.md",
        "package.json",
        "README.md",
        "_gitignore",
        "pnpm-workspace.yaml",
        "netlify.toml",
        "vercel.json",
        "components/Counter.vue",
        "snippets/external.ts",
        "pages/imported-slides.md",
    )

    /** `create-slidev` ships dotfiles under safe names and renames them on scaffold. */
    private val RENAMES = mapOf("_gitignore" to ".gitignore")

    @Throws(IOException::class)
    fun writeTo(targetDir: Path, projectName: String, packageManager: PackageManager = PackageManager.NPM) {
        val packageName = sanitizePackageName(projectName)
        for (file in FILES) {
            val resource = "$RESOURCE_ROOT/$file"
            val content = SlidevStarterTemplate::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
                ?: throw IOException("Missing bundled template resource: $resource")
            val target = targetDir.resolve(RENAMES[file] ?: file)
            target.parent?.let { Files.createDirectories(it) }
            Files.write(target, transform(file, content, packageName, packageManager))
        }
    }

    private fun transform(
        file: String,
        content: ByteArray,
        packageName: String,
        packageManager: PackageManager,
    ): ByteArray = when (file) {
        "package.json" -> String(content, Charsets.UTF_8)
            .replace("__PROJECT_NAME__", packageName)
            .toByteArray()
        // The counterpart of create-slidev's writeReadme(): point the instructions at the chosen tool.
        "README.md" -> String(content, Charsets.UTF_8)
            .replace("npm install", "${packageManager.displayName} install")
            .replace("npm run dev", "${packageManager.displayName} run dev")
            .toByteArray()
        else -> content
    }

    /** Derives a valid npm package name from the project directory name, like `create-slidev` does. */
    fun sanitizePackageName(raw: String): String {
        val name = raw.trim().lowercase()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^a-z0-9._-]"), "-")
            .trim('-', '.', '_')
        return name.ifEmpty { "slidev-project" }
    }
}
