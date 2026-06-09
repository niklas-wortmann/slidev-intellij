package dev.slidev.intellij.editor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Platform tests for the JS/TS support in slide content (plan.md, 18.4): the probe test
 * pins the HTML template-data root shape everything relies on (18.1, "Path B" — script
 * bodies are lexer-embedded JS PSI, attribute values are plain injection hosts), the rest
 * cover completion in script bodies, the script-body coloring annotator, phantom-error
 * suppression, the paren-wrapped attribute injection, and the negatives (non-Slidev
 * files, v-for, plain attributes, fenced code).
 */
class SlidevScriptInjectionTest : BasePlatformTestCase() {

    private fun configure(text: String, vararg extraFiles: Pair<String, String>) {
        configureCaretFile("slides.md", text, *extraFiles)
    }

    private fun configureCaretFile(caretPath: String, text: String, vararg extraFiles: Pair<String, String>) {
        for ((path, content) in extraFiles) {
            myFixture.addFileToProject(path, content)
        }
        val psiFile = myFixture.addFileToProject(caretPath, text)
        SlidevProjectService.getInstance(project).rescanSync()
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    }

    /**
     * The injected file at the caret, or null when the caret is in plain host text —
     * the fixture switches [myFixture.file] to the injected fragment automatically
     * whenever the caret lands inside an injection.
     */
    private fun injectedFileAtCaret(): com.intellij.psi.PsiFile? =
        myFixture.file.takeIf { InjectedLanguageManager.getInstance(project).isInjectedFragment(it) }

    private fun injectedLanguageIdAtCaret(): String? = injectedFileAtCaret()?.language?.id

    private fun injectedFileTextAtCaret(): String? = injectedFileAtCaret()?.text

    private fun warningsAndErrors(): List<HighlightInfo> =
        myFixture.doHighlighting().filter { it.severity >= HighlightSeverity.WARNING }

    // ------------------------------------------------------------------ probe (18.1)

    fun `test html root exposes script body and bound attribute as injection hosts`() {
        configure(
            """
            # Title

            <div v-motion :initial="{ x: -80 }" :enter="{ x: 0 }">Slidev</div>

            <script setup lang="ts">
            const final = { x: 0, transition: { type: 'spring' } }

            const afterBlankLine = 1
            </script>
            """.trimIndent(),
        )
        val htmlRoot = myFixture.file.viewProvider.getPsi(HTMLLanguage.INSTANCE)
        assertNotNull("markdown file has no HTML template-data root", htmlRoot)

        val script = PsiTreeUtil.findChildrenOfType(htmlRoot, XmlTag::class.java)
            .firstOrNull { it.localName.equals("script", ignoreCase = true) }
        assertNotNull("no <script> tag parsed in the HTML root", script)

        // Path B (spike outcome, 18.1): the JavaScript plugin's HtmlEmbeddedContentSupport
        // already embeds the script body as JS PSI during the template-data lexing — there
        // is no XmlText host to inject into, and none is needed at the PSI level.
        val embedded = script!!.children.firstOrNull { SlidevScriptSupport.isJavaScriptKind(it) }
        assertNotNull("script body is no longer lexer-embedded JS — Path A re-applies, re-plan", embedded)

        val attrValue = PsiTreeUtil.findChildrenOfType(htmlRoot, XmlAttributeValue::class.java)
            .firstOrNull { it.value.contains("-80") }
        assertNotNull("no XmlAttributeValue for :initial in the HTML root", attrValue)
        assertTrue((attrValue as PsiLanguageInjectionHost).isValidHost)
    }

    // ------------------------------------------------------------------ script bodies

    fun `test completion works inside script body`() {
        configure(
            """
            # Title

            <script setup>
            (1.5).toF<caret>
            </script>
            """.trimIndent(),
        )
        val lookups = myFixture.completeBasic()?.map { it.lookupString }
        // A single match is auto-inserted (lookups == null), multiple are listed.
        assertTrue(
            "no JS completion through the embedded script body",
            lookups?.contains("toFixed") ?: myFixture.editor.document.text.contains("toFixed"),
        )
    }

    fun `test script body gets lexer coloring annotations`() {
        configure(
            """
            # Title

            <script setup lang="ts">
            const greeting = 'hi'
            </script>
            """.trimIndent(),
        )
        val constOffset = myFixture.editor.document.text.indexOf("const")
        val keyword = myFixture.doHighlighting().firstOrNull {
            it.severity == HighlightSeverity.INFORMATION &&
                it.startOffset == constOffset && it.endOffset == constOffset + "const".length &&
                it.forcedTextAttributesKey?.externalName?.contains("KEYWORD") == true
        }
        assertNotNull("no keyword coloring annotation on `const` in the script body", keyword)
    }

    fun `test phantom parse errors are suppressed in slidev deck`() {
        configure(
            """
            # Title

            <script setup lang="ts">
            const greeting: string = 'hi'

            const second = greeting.length
            </script>
            """.trimIndent(),
        )
        // Without the filter the embedded parse reports "Newline or semicolon expected"
        // on semicolon-less statements (newlines are swallowed into MARKDOWN_OUTER_BLOCK).
        assertEmpty(warningsAndErrors())
    }

    fun `test phantom parse errors remain in non-slidev markdown`() {
        configureCaretFile(
            "notes.md",
            "# Notes\n\n<script setup>\nconst a = 1\n\nconst b = 2\n</script>",
            "slides.md" to "# Deck",
        )
        // The filter is scoped to Slidev decks; other markdown keeps platform behavior.
        assertNotEmpty(warningsAndErrors())
    }

    // ------------------------------------------------------------- attribute values

    fun `test bound attribute value is injected as parenthesized expression`() {
        configure("# Title\n\n<div v-motion :initial=\"{ x: -<caret>80 }\">x</div>")
        assertEquals("JavaScript", injectedLanguageIdAtCaret())
        assertEquals("({ x: -80 })", injectedFileTextAtCaret())
    }

    fun `test event attribute value is injected without parens`() {
        configure("# Title\n\n<div @click=\"cou<caret>nt++\">x</div>")
        assertEquals("JavaScript", injectedLanguageIdAtCaret())
        assertEquals("count++", injectedFileTextAtCaret())
    }

    fun `test directive expression value is injected`() {
        configure("# Title\n\n<div v-if=\"rea<caret>dy\">x</div>")
        assertEquals("JavaScript", injectedLanguageIdAtCaret())
    }

    fun `test v-for value is not injected`() {
        configure("# Title\n\n<div v-for=\"i<caret>tem in items\">x</div>")
        assertNull(injectedLanguageIdAtCaret())
    }

    fun `test plain attribute value is not injected`() {
        configure("# Title\n\n<div class=\"te<caret>xt\">x</div>")
        assertNull(injectedLanguageIdAtCaret())
    }

    fun `test injected attribute fragment produces highlighting infos`() {
        configure("# Title\n\n<div :initial=\"{ x: -80 }\">x</div>")
        // The daemon must render injection-derived infos for non-primary-root hosts —
        // the programmatic answer to the "do daemon passes cover the HTML root" risk.
        val numberOffset = myFixture.editor.document.text.indexOf("80")
        val infos = myFixture.doHighlighting()
            .filter { it.startOffset <= numberOffset && numberOffset < it.endOffset }
        assertTrue(
            "no highlighting infos over the injected attribute expression",
            infos.isNotEmpty(),
        )
    }

    // ------------------------------------------------------------------- negatives

    fun `test fenced code is unaffected`() {
        configure("# Title\n\n```html\n<div :initial=\"{ x: <caret>1 }\">x</div>\n```\n")
        assertTrue(injectedLanguageIdAtCaret() != "JavaScript")
    }

    fun `test non-slidev markdown gets no injection`() {
        configureCaretFile(
            "notes.md",
            "# Notes\n\n<div :initial=\"{ x: <caret>1 }\">x</div>",
            "slides.md" to "# Deck",
        )
        assertNull(injectedLanguageIdAtCaret())
    }

    fun `test component completion does not fire inside script body`() {
        configure(
            """
            # Title

            <script setup>
            const a = 1 < T<caret>
            </script>
            """.trimIndent(),
        )
        val lookups = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertDoesntContain(lookups, "Tweet", "Toc")
    }
}
