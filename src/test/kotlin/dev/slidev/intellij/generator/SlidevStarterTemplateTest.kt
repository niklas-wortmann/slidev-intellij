package dev.slidev.intellij.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class SlidevStarterTemplateTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `every manifest entry resolves as a bundled resource`() {
        for (file in SlidevStarterTemplate.FILES) {
            assertNotNull(
                "Missing bundled template resource: $file",
                SlidevStarterTemplate::class.java.getResourceAsStream("/slidev/template/$file"),
            )
        }
    }

    @Test
    fun `writeTo creates all starter files including nested directories`() {
        val target = tempDir.newFolder("deck").toPath()
        SlidevStarterTemplate.writeTo(target, "deck")

        val expected = listOf(
            "slides.md", "package.json", "README.md", ".gitignore", "pnpm-workspace.yaml",
            "netlify.toml", "vercel.json",
            "components/Counter.vue", "snippets/external.ts", "pages/imported-slides.md",
        )
        for (file in expected) {
            val path = target.resolve(file)
            assertTrue("Expected $file to exist", Files.isRegularFile(path))
            assertTrue("Expected $file to be non-empty", Files.size(path) > 0)
        }
        // The dotfile is written under its real name, not the bundled `_gitignore` alias.
        assertFalse(Files.exists(target.resolve("_gitignore")))
    }

    @Test
    fun `package json gets the sanitized project name and no placeholder leftovers`() {
        val target = tempDir.newFolder("My Deck").toPath()
        SlidevStarterTemplate.writeTo(target, "My Deck")

        val packageJson = Files.readString(target.resolve("package.json"))
        assertTrue(packageJson.contains("\"name\": \"my-deck\""))
        assertFalse(packageJson.contains("__PROJECT_NAME__"))
        assertFalse(packageJson.contains("packageManager"))
    }

    @Test
    fun `readme instructions follow the chosen package manager`() {
        val target = tempDir.newFolder("deck").toPath()
        SlidevStarterTemplate.writeTo(target, "deck", PackageManager.PNPM)

        val readme = Files.readString(target.resolve("README.md"))
        assertTrue(readme.contains("`pnpm install`"))
        assertTrue(readme.contains("`pnpm run dev`"))
        assertFalse(readme.contains("`npm install`"))
    }

    @Test
    fun `slides md only references files that ship with the template`() {
        val target = tempDir.newFolder("deck").toPath()
        SlidevStarterTemplate.writeTo(target, "deck")

        val slides = Files.readString(target.resolve("slides.md"))
        Regex("""src: (\S+)""").findAll(slides).forEach { match ->
            val referenced = match.groupValues[1].removePrefix("./")
            assertTrue("slides.md references missing $referenced", Files.exists(target.resolve(referenced)))
        }
    }

    @Test
    fun `sanitizePackageName handles spaces case and invalid characters`() {
        assertEquals("my-deck", SlidevStarterTemplate.sanitizePackageName("My Deck"))
        assertEquals("talk-2026", SlidevStarterTemplate.sanitizePackageName("Talk 2026!"))
        assertEquals("deck", SlidevStarterTemplate.sanitizePackageName("..deck--"))
        assertEquals("slidev-project", SlidevStarterTemplate.sanitizePackageName("   "))
        assertEquals("slidev-project", SlidevStarterTemplate.sanitizePackageName("---"))
    }
}
