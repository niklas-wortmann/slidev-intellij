package dev.slidev.intellij.editor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Platform tests for the semantic coloring annotator (plan.md, 15.1): component-tag
 * and Vue-attribute colors, the unknown-component weak warning, and that plain HTML
 * and non-Slidev markdown stay untouched.
 */
class SlidevComponentHighlightingTest : BasePlatformTestCase() {

    private fun highlight(text: String, vararg extraFiles: Pair<String, String>): List<HighlightInfo> =
        highlightFile("slides.md", text, *extraFiles)

    private fun highlightFile(path: String, text: String, vararg extraFiles: Pair<String, String>): List<HighlightInfo> {
        for ((extraPath, content) in extraFiles) {
            myFixture.addFileToProject(extraPath, content)
        }
        val psiFile = myFixture.addFileToProject(path, text)
        SlidevProjectService.getInstance(project).rescanSync()
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
        return myFixture.doHighlighting()
    }

    private fun List<HighlightInfo>.texts(key: TextAttributesKey): List<String> =
        filter { it.forcedTextAttributesKey == key }
            .map { myFixture.editor.document.getText(TextRange(it.startOffset, it.endOffset)) }

    private fun List<HighlightInfo>.unknownComponentWarnings(): List<HighlightInfo> =
        filter { it.severity == HighlightSeverity.WEAK_WARNING && it.description.orEmpty().contains("Unknown component") }

    fun `test known component tags are colored`() {
        val infos = highlight("# Title\n\n<Tweet id=\"1\" />\n\n<Toc>\n</Toc>")
        assertEquals(listOf("Tweet", "Toc", "Toc"), infos.texts(SlidevHighlightColors.COMPONENT_TAG))
        assertEmpty(infos.unknownComponentWarnings())
    }

    fun `test local component is recognized`() {
        val infos = highlight(
            "# Title\n\n<Counter :count=\"1\" />",
            "components/Counter.vue" to "<template><div/></template>",
        )
        assertEquals(listOf("Counter"), infos.texts(SlidevHighlightColors.COMPONENT_TAG))
        assertEmpty(infos.unknownComponentWarnings())
    }

    fun `test unknown pascal-case tag gets a weak warning`() {
        val infos = highlight("# Title\n\n<NoSuchThing>\n</NoSuchThing>")
        // Reported once, on the opening tag only.
        val warning = assertOneElement(infos.unknownComponentWarnings())
        assertEquals("NoSuchThing", myFixture.editor.document.getText(TextRange(warning.startOffset, warning.endOffset)))
        assertEmpty(infos.texts(SlidevHighlightColors.COMPONENT_TAG))
    }

    fun `test plain html tags are not flagged or colored`() {
        val infos = highlight("# Title\n\n<div class=\"x\">text</div>")
        assertEmpty(infos.unknownComponentWarnings())
        assertEmpty(infos.texts(SlidevHighlightColors.COMPONENT_TAG))
    }

    fun `test directive and bound attributes are colored`() {
        val infos = highlight("# Title\n\n<div v-click :class=\"c\" @click=\"go()\">x</div>")
        assertEquals(listOf("v-click"), infos.texts(SlidevHighlightColors.DIRECTIVE_ATTRIBUTE))
        assertEquals(listOf(":class", "@click"), infos.texts(SlidevHighlightColors.BOUND_ATTRIBUTE))
    }

    fun `test fenced code and comments are not annotated`() {
        val infos = highlight("# Title\n\n```html\n<NoSuchThing v-click />\n```\n\n<!-- <NoSuchThing /> -->")
        assertEmpty(infos.unknownComponentWarnings())
        assertEmpty(infos.texts(SlidevHighlightColors.DIRECTIVE_ATTRIBUTE))
    }

    fun `test non-slidev markdown is untouched`() {
        val infos = highlightFile("notes.md", "# Notes\n\n<NoSuchThing v-click />", "slides.md" to "# Deck")
        assertEmpty(infos.unknownComponentWarnings())
        assertEmpty(infos.texts(SlidevHighlightColors.COMPONENT_TAG))
        assertEmpty(infos.texts(SlidevHighlightColors.DIRECTIVE_ATTRIBUTE))
    }
}
