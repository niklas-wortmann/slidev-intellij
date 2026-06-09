package dev.slidev.intellij.editor

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.platform.backend.documentation.impl.computeDocumentationBlocking
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.slidev.intellij.project.SlidevProjectService

/**
 * Platform tests for the component support in slide content (plan.md, 14.6): tag and
 * attribute completion over the component index, hover/lookup documentation, go-to
 * declaration, and that non-Slidev markdown stays unaffected.
 */
class SlidevComponentSupportTest : BasePlatformTestCase() {

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

    private fun lookupStrings(): List<String> =
        myFixture.completeBasic()?.map { it.lookupString }.orEmpty()

    private fun selectLookupItem(lookupString: String) {
        val item = myFixture.lookupElements!!.first { it.lookupString == lookupString }
        (myFixture.lookup as LookupImpl).currentItem = item
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    }

    private fun docAtCaret(): String? {
        val targets = IdeDocumentationTargetProvider.getInstance(project)
            .documentationTargets(myFixture.editor, myFixture.file, myFixture.caretOffset)
        return targets.firstNotNullOfOrNull { computeDocumentationBlocking(it.createPointer())?.html }
    }

    private fun gotoTargetsAtCaret(): Array<out com.intellij.psi.PsiElement>? {
        val offset = myFixture.caretOffset
        val element = myFixture.file.findElementAt(offset)
        return SlidevComponentGotoDeclarationHandler().getGotoDeclarationTargets(element, offset, myFixture.editor)
    }

    // ------------------------------------------------------------ tag completion

    fun `test tag completion offers builtins and local components`() {
        configure(
            "# Title\n\n<T<caret>",
            "components/TipBox.vue" to "<template><div/></template>",
        )
        assertContainsElements(lookupStrings(), "Tweet", "Toc", "TipBox")
    }

    fun `test tag completion inserts a self-closing tail`() {
        configure("# Title\n\n<Twe<caret>")
        myFixture.completeBasic()
        if (myFixture.lookupElements != null) {
            selectLookupItem("Tweet")
        }
        assertTrue(myFixture.editor.document.text.contains("<Tweet />"))
    }

    fun `test closing tag completion does not self-close`() {
        configure("# Title\n\n<Toc>\n</To<caret>")
        myFixture.completeBasic()
        if (myFixture.lookupElements != null) {
            selectLookupItem("Toc")
        }
        assertFalse(myFixture.editor.document.text.contains("</Toc />"))
    }

    fun `test no component items in fenced code or frontmatter`() {
        configure("# Title\n\n```html\n<T<caret>\n```\n")
        assertDoesntContain(lookupStrings(), "Tweet", "Toc")
    }

    fun `test no component items in non-slidev markdown`() {
        configureCaretFile("notes.md", "# Notes\n\n<T<caret>", "slides.md" to "# Deck")
        assertDoesntContain(lookupStrings(), "Tweet", "Toc")
    }

    // ------------------------------------------------------ attribute completion

    fun `test attribute completion offers props and bound variants`() {
        configure("# Title\n\n<Tweet <caret>")
        val lookups = lookupStrings()
        assertContainsElements(lookups, "id", ":id", "scale", ":scale")
    }

    fun `test attribute completion offers global directives`() {
        configure("# Title\n\n<div <caret>")
        assertContainsElements(lookupStrings(), "v-click", "v-after", "v-motion", "v-mark", "v-drag")
    }

    fun `test attribute completion inserts value quotes`() {
        configure("# Title\n\n<Tweet i<caret> />")
        myFixture.completeBasic()
        if (myFixture.lookupElements != null) {
            selectLookupItem("id")
        }
        assertTrue(myFixture.editor.document.text.contains("<Tweet id=\"\" />"))
    }

    fun `test local component props are offered`() {
        configure(
            "# Title\n\n<Counter <caret>",
            "components/Counter.vue" to
                "<script setup>const props = defineProps({ count: { type: Number, required: true } })</script>",
        )
        assertContainsElements(lookupStrings(), "count", ":count")
    }

    // ------------------------------------------------------------- documentation

    fun `test hover documentation on a component tag`() {
        configure("# Title\n\n<Twe<caret>et id=\"1\" />")
        val html = docAtCaret()
        assertNotNull("expected hover documentation for the Tweet tag", html)
        assertTrue(html!!.contains("Embed a tweet"))
    }

    fun `test hover documentation on a prop`() {
        configure("# Title\n\n<Tweet i<caret>d=\"1\" />")
        val html = docAtCaret()
        assertNotNull("expected hover documentation for the 'id' prop", html)
        assertTrue(html!!.contains("id of the tweet"))
    }

    fun `test hover documentation on a directive`() {
        configure("# Title\n\n<div v-cli<caret>ck=\"2\">x</div>")
        val html = docAtCaret()
        assertNotNull("expected hover documentation for 'v-click'", html)
        assertTrue(html!!.contains("v-click"))
    }

    fun `test documentation for a tag completion lookup item`() {
        // Prefix "T" matches several components, so the lookup is guaranteed to show.
        configure("# Title\n\n<T<caret>")
        myFixture.completeBasic()
        val lookupElement = myFixture.lookupElements?.firstOrNull { it.lookupString == "Tweet" }
        assertNotNull("expected a 'Tweet' lookup element", lookupElement)
        val target = IdeDocumentationTargetProvider.getInstance(project)
            .documentationTargets(myFixture.editor, myFixture.file, lookupElement!!)
            .firstOrNull()
        assertNotNull("expected a documentation target for the 'Tweet' lookup item", target)
        val html = computeDocumentationBlocking(target!!.createPointer())?.html
        assertNotNull(html)
        assertTrue(html!!.contains("Embed a tweet"))
    }

    fun `test no hover documentation on unknown tags`() {
        configure("# Title\n\n<NoSu<caret>chThing />")
        assertNull(docAtCaret())
    }

    // ----------------------------------------------------------------- navigation

    fun `test goto declaration from local component to its vue file`() {
        configure(
            "# Title\n\n<Cou<caret>nter :count=\"1\" />",
            "components/Counter.vue" to "<template><div/></template>",
        )
        val target = gotoTargetsAtCaret()?.singleOrNull() as? PsiFile
        assertEquals("Counter.vue", target?.name)
    }

    fun `test goto declaration from builtin opens its docs url`() {
        configure("# Title\n\n<Twe<caret>et id=\"1\" />")
        val target = gotoTargetsAtCaret()?.singleOrNull()
        val docs = target as? SlidevComponentGotoDeclarationHandler.DocsUrlElement
        assertNotNull("expected a docs-url target for the built-in Tweet", docs)
        assertEquals("https://sli.dev/builtin/components#tweet", docs!!.url)
    }

    fun `test no goto declaration on unknown tags or plain text`() {
        configure("# Title\n\n<NoSu<caret>chThing /> plain")
        assertNull(gotoTargetsAtCaret())
    }
}
