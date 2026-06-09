package dev.slidev.intellij.editor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection
import com.intellij.codeInspection.htmlInspections.HtmlUnknownBooleanAttributeInspection
import com.intellij.codeInspection.htmlInspections.HtmlUnknownTagInspection
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Platform tests for [SlidevHtmlInspectionSuppressor]: the HTML inspections that run on
 * the markdown file's HTML template-data root must not flag Slidev component tags
 * (including kebab-case usage) or Vue-directive/attributify attribute names in decks,
 * while keeping their regular behavior for genuinely unknown tags and non-Slidev files.
 */
class SlidevHtmlInspectionSuppressionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(
            HtmlUnknownTagInspection(),
            HtmlUnknownAttributeInspection(),
            HtmlUnknownBooleanAttributeInspection(),
        )
    }

    private fun highlight(text: String): List<HighlightInfo> = highlightFile("slides.md", text)

    private fun highlightFile(path: String, text: String, vararg extraFiles: Pair<String, String>): List<HighlightInfo> {
        for ((extraPath, content) in extraFiles) {
            myFixture.addFileToProject(extraPath, content)
        }
        val psiFile = myFixture.addFileToProject(path, text)
        SlidevProjectService.getInstance(project).rescanSync()
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
        return myFixture.doHighlighting().filter { it.severity >= HighlightSeverity.WEAK_WARNING }
    }

    fun `test kebab-case component tags are not flagged as unknown tags`() {
        assertEmpty(highlight("# Title\n\n<v-click>\n\nstep\n\n</v-click>"))
    }

    fun `test directive and attributify attributes are not flagged`() {
        assertEmpty(
            highlight(
                "# Title\n\n<div v-click.fade mt-12 v-mark.circle.orange=\"8\">x</div>\n\n<p v-after>y</p>",
            ),
        )
    }

    fun `test html fence inside a deck is not flagged either`() {
        assertEmpty(highlight("# Title\n\n```html\n<span v-mark.underline.orange>marked</span>\n```"))
    }

    fun `test non-slidev markdown keeps platform warnings`() {
        // Also proves the inspections fire in this setup — suppression is targeted, not
        // blanket. (HtmlUnknownTag itself never fires on markdown HTML roots in the test
        // platform; in full IDEs the web-symbols layer reports it under the same toolId.)
        assertNotEmpty(
            highlightFile(
                "notes.md",
                "# Notes\n\n<div v-click.fade>x</div>",
                "slides.md" to "# Deck",
            ),
        )
    }
}
