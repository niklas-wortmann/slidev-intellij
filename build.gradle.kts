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
    }
}
