import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // Bundle our own SnakeYAML copy — the platform's version drifts across releases/IDEs,
    // and the plugin classloader resolves the plugin's own jars first.
    implementation(libs.snakeyaml)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.3.5")
        testFramework(TestFrameworkType.Platform)

        // Markdown provides the "Markdown" language our optional folding registration targets;
        // needed on the sandbox/test classpath (the plugin dependency itself is optional, see plugin.xml).
        bundledPlugin("org.intellij.plugins.markdown")

        // MCP server (bundled since 2025.2) defines the optional `mcpToolset` EP our
        // LM tools register against (see slidev-mcp.xml).
        bundledPlugin("com.intellij.mcpServer")

        pluginVerifier()
        zipSigner()
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("version")

        ideaVersion {
            sinceBuild = "253"
            // Open-ended: the plugin sticks to stable platform APIs; compatibility is
            // re-checked per release by the Plugin Verifier (see verifyPlugin).
            untilBuild = provider { null }
        }

        // Rendered eagerly: the changelog extension holds a Project reference, which a lazy
        // provider would drag into the configuration cache.
        changeNotes = with(changelog) {
            renderItem(
                (getOrNull(providers.gradleProperty("version").get()) ?: getUnreleased())
                    .withHeader(false)
                    .withEmptySections(false),
                Changelog.OutputType.HTML,
            )
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // Pre-release suffix selects the Marketplace channel: 0.1.0-alpha.1 -> "alpha",
        // plain 0.1.0 -> "default" (the stable channel every user sees).
        channels = providers.gradleProperty("version").map {
            listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
}

changelog {
    groups.empty()
}
