package dev.slidev.intellij.editor

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue

/**
 * JavaScript injection into Vue expression attribute values in slide content (plan.md,
 * 18.2): `:x`/`v-bind` and `v-*` expression values wrapped in parentheses (so `{ x: -80 }`
 * parses as an object expression, not a block statement), `@x`/`v-on` handler values bare
 * (Vue allows statement bodies like `count++` there). Targets `XmlAttributeValue` in the
 * markdown file's HTML template-data root — see [SlidevScriptSupport]. Script *bodies*
 * need no injection: the JavaScript plugin lexer-embeds them as JS PSI (18.1, "Path B"),
 * which [SlidevScriptBodyAnnotator] colors and [SlidevScriptErrorFilter] de-noises.
 *
 * Values are always plain JavaScript, even when the slide's script block is `lang="ts"` —
 * Vue template expressions are JS; per-fragment TS machinery buys nothing here. The full
 * TypeScript service does not run on injected fragments either; completion/highlighting
 * come from the built-in JS resolver, which is the feature scope (highlighting + basic
 * completion), not full type checking.
 */
internal class SlidevScriptInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(XmlAttributeValue::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val host = context as? XmlAttributeValue ?: return
        if (host !is PsiLanguageInjectionHost || !host.isValidHost) return
        val file = context.containingFile ?: return
        if (!SlidevScriptSupport.isSlidevSlideHtmlRoot(file)) return

        val name = (host.parent as? XmlAttribute)?.name ?: return
        val wrap = when {
            name.startsWith(":") || name.startsWith("v-bind") -> true          // expression: parenthesize
            name.startsWith("@") || name.startsWith("v-on") -> false           // handler: statements allowed
            name.startsWith("v-") &&
                name.substringBefore(':').substringBefore('.') !in VALUELESS_OR_SPECIAL -> true
            else -> return
        }
        if (host.value.isBlank()) return
        val range = ElementManipulators.getValueTextRange(host)
        if (range.isEmpty) return
        val language = Language.findLanguageByID("JavaScript") ?: return

        registrar.startInjecting(language, "js")
            .addPlace(if (wrap) "(" else null, if (wrap) ")" else null, host, range)
            .doneInjecting()
    }

    companion object {
        // No expression value: v-else/v-pre/v-cloak/v-once. Special non-JS grammar: v-for
        // (`item in items`), v-slot (slot-props destructuring; the `#` shorthand never hits
        // the `v-` branch anyway).
        private val VALUELESS_OR_SPECIAL = setOf("v-else", "v-pre", "v-cloak", "v-once", "v-for", "v-slot")
    }
}
