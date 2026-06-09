package dev.slidev.intellij.editor

import com.intellij.lang.Language
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag

/**
 * Lexer-driven coloring of inline `<script>` bodies in slide content (plan.md, 18.2):
 * the JavaScript plugin embeds the body as JS PSI in the HTML template-data root (18.1,
 * "Path B"), so completion works, but the editor highlighter is the Markdown lexer and
 * paints the body as flat text. Like [SlidevComponentAnnotator] this layers the missing
 * colors back on as INFORMATION annotations — here by running the JS/TS syntax
 * highlighter's lexer over the embedded range. The lexer honors `lang="ts"` even though
 * the embedded *PSI* is always a plain-JS dialect, so TS bodies still color correctly.
 */
internal class SlidevScriptBodyAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Annotate once per file (this root's file element is the HtmlFile), not per element.
        if (element !is PsiFile) {
            return
        }
        if (!SlidevScriptSupport.isSlidevSlideHtmlRoot(element)) {
            return
        }
        for (tag in PsiTreeUtil.findChildrenOfType(element, XmlTag::class.java)) {
            if (!tag.localName.equals("script", ignoreCase = true)) {
                continue
            }
            val body = tag.children.firstOrNull { SlidevScriptSupport.isJavaScriptKind(it) } ?: continue
            annotateBody(element, tag, body, holder)
        }
    }

    private fun annotateBody(file: PsiFile, tag: XmlTag, body: PsiElement, holder: AnnotationHolder) {
        val lang = tag.getAttributeValue("lang")?.trim()?.lowercase()
        val language = Language.findLanguageByID(if (lang in TS_LANGS) "TypeScript" else "JavaScript") ?: return
        val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(
            language, file.project, file.viewProvider.virtualFile,
        ) ?: return

        // Template-data roots share document offsets, so the embedded range maps 1:1.
        val base = body.textRange.startOffset
        val lexer = highlighter.highlightingLexer
        lexer.start(body.text)
        while (true) {
            val tokenType = lexer.tokenType ?: break
            val key = highlighter.getTokenHighlights(tokenType).lastOrNull()
            if (key != null) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(TextRange(base + lexer.tokenStart, base + lexer.tokenEnd))
                    .textAttributes(key)
                    .create()
            }
            lexer.advance()
        }
    }

    companion object {
        private val TS_LANGS = setOf("ts", "typescript")
    }
}
