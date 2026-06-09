package dev.slidev.intellij.editor

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.slidev.intellij.project.SlidevProjectService
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence

/**
 * Platform tests for the Magic Move per-step injection (plan.md, 19.7), plus the 19.1
 * spike pins kept as regression tests: the markdown plugin's guesser space-chops info
 * strings (so `md magic-move` resolves to a whole-fence Markdown injection and a plain
 * ` ```js {*|2|5-6} ` fence already resolves to JavaScript — 19.8 is moot), but fences
 * nested inside *injected* markdown never get recursive injection — which is why the
 * Design-B [SlidevMagicMoveInjector] exists. Gotcha from 18.4 applies: with the caret
 * inside an injection, [myFixture.file] *is* the fragment file.
 */
class SlidevMagicMoveInjectionTest : BasePlatformTestCase() {

    private fun configure(text: String): PsiFile = configureFile("slides.md", text)

    private fun configureFile(path: String, text: String): PsiFile {
        // A registered deck must exist either way; for non-deck files it is a sibling.
        if (path != "slides.md") myFixture.addFileToProject("slides.md", "# Deck")
        val psiFile = myFixture.addFileToProject(path, text)
        SlidevProjectService.getInstance(project).rescanSync()
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
        return myFixture.file
    }

    private fun magicMoveFence(): MarkdownCodeFence =
        PsiTreeUtil.findChildrenOfType(myFixture.file, MarkdownCodeFence::class.java)
            .first { it.text.startsWith("````") }

    private fun injectedLanguages(fence: MarkdownCodeFence): List<String> =
        InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(fence)
            ?.map { it.first.language.id }
            .orEmpty()

    private fun injectedTexts(fence: MarkdownCodeFence): List<String> =
        InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(fence)
            ?.map { it.first.containingFile.text }
            .orEmpty()

    private fun deck(body: String) = "# Title\n\n$body"

    // --------------------------------------------------------------- per-step injection

    fun `test steps are injected with their own languages`() {
        configure(deck("````md magic-move\n```js\nconst a = 1\n```\n```ts\nconst b: number = 2\n```\n````\n"))
        val fence = magicMoveFence()
        assertEquals(listOf("JavaScript", "TypeScript"), injectedLanguages(fence))
        assertEquals(listOf("const a = 1", "const b: number = 2"), injectedTexts(fence))
    }

    fun `test step meta is stripped from the language token`() {
        configure(
            deck("````md magic-move\n```js {*|2|5-6}\nlet a\n```\n```ts {*}{lines:false}\nlet b\n```\n```ts{2,3}\nlet c\n```\n````\n"),
        )
        assertEquals(listOf("JavaScript", "TypeScript", "TypeScript"), injectedLanguages(magicMoveFence()))
    }

    fun `test outer fence options and title do not break injection`() {
        configure(deck("````md magic-move {at:4, lines:true} [app.js]\n```js\nconst a = 1\n```\n````\n"))
        assertEquals(listOf("JavaScript"), injectedLanguages(magicMoveFence()))
    }

    fun `test empty and unresolvable steps are skipped, others still inject`() {
        configure(deck("````md magic-move\n```js\n```\n```nosuchlang\nx\n```\n```ts\nlet b\n```\n````\n"))
        assertEquals(listOf("TypeScript"), injectedLanguages(magicMoveFence()))
    }

    fun `test crlf steps inject the content without carriage returns`() {
        configure(deck("````md magic-move\r\n```js\r\nconst a = 1\r\n```\r\n````\r\n"))
        // The document normalizes to \n, but the scanner must stay CRLF-safe either way.
        assertEquals(listOf("const a = 1"), injectedTexts(magicMoveFence()))
    }

    fun `test non-code text between steps stays uninjected`() {
        configure(deck("````md magic-move\nThis is a comment\n```js\nconst a = 1\n```\n````\n"))
        assertEquals(listOf("const a = 1"), injectedTexts(magicMoveFence()))
    }

    fun `test step content gets syntax coloring`() {
        configure(deck("````md magic-move\n```js\nconst a = 1\n```\n````\n"))
        val offset = myFixture.editor.document.text.indexOf("const a")
        val syntax = myFixture.doHighlighting().filter {
            it.startOffset <= offset && offset < it.endOffset &&
                it.severity.name.contains("INJECTED_FRAGMENT")
        }
        assertTrue("no injected-fragment coloring over the step content", syntax.isNotEmpty())
    }

    fun `test completion works inside a step`() {
        configure(deck("````md magic-move\n```js\n(1.5).toF<caret>\n```\n````\n"))
        // With the caret inside the injection, myFixture.file is the fragment (18.4 gotcha).
        assertEquals("JavaScript", myFixture.file.language.id)
        val lookups = myFixture.completeBasic()?.map { it.lookupString }
        assertTrue(
            "no JS completion inside the magic-move step",
            lookups?.contains("toFixed") ?: myFixture.editor.document.text.contains("toFixed"),
        )
    }

    // -------------------------------------------------------------------- error noise

    fun `test parse errors in steps are suppressed`() {
        configure(deck("````md magic-move\n```js\nconst = {\n```\n```ts\nconst a = 1\n```\n````\n"))
        // Mid-animation steps are intentionally incomplete; without SlidevMagicMoveErrorFilter
        // the broken step reports "Variable name expected" / "Missing }" (19.5).
        assertEmpty(myFixture.doHighlighting().filter { it.severity >= HighlightSeverity.WARNING })
    }

    fun `test parse errors outside magic-move are not suppressed`() {
        configure(deck("```js\nconst = {\n```\n"))
        // The filter is scoped to magic-move hosts; a regular fence keeps its errors.
        assertNotEmpty(myFixture.doHighlighting().filter { it.severity >= HighlightSeverity.WARNING })
    }

    // ----------------------------------------------------------- scope & 19.1 regressions

    fun `test non-slidev markdown keeps the platform markdown injection`() {
        configureFile("notes.md", "# Notes\n\n````md magic-move\n```js\nconst a = 1\n```\n````\n")
        // 19.1(a) pin: the guesser space-chops `md magic-move` to `md`, so the platform
        // injects Markdown into the whole fence — and (b) fences nested in *injected*
        // markdown get no recursive injection, so steps would stay plain without our
        // injector. The injector is deck-guarded and must not fire here.
        assertEquals(listOf("Markdown"), injectedLanguages(magicMoveFence()))
    }

    fun `test plain md fence in a deck is not treated as magic-move`() {
        configure(deck("````md\n```js\nconst a = 1\n```\n````\n"))
        assertEquals(listOf("Markdown"), injectedLanguages(magicMoveFence()))
    }

    fun `test top-level fence with line-highlight meta resolves today`() {
        // 19.1(c) pin: ` ```js {*|2|5-6} ` already resolves via the guesser's space-chopping,
        // so the 19.8 stretch (own fenceLanguageProvider for regular fences) stays moot.
        configure(deck("```js {*|2|5-6}\nconst top = 1\n```\n"))
        val fence = PsiTreeUtil.findChildrenOfType(myFixture.file, MarkdownCodeFence::class.java).single()
        assertEquals(listOf("JavaScript"), injectedLanguages(fence))
    }
}
