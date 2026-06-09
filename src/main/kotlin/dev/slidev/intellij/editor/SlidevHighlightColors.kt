package dev.slidev.intellij.editor

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

/**
 * Text attribute keys for the semantic coloring of slide content (plan.md, 15.1),
 * applied by [SlidevComponentAnnotator] and configurable on [SlidevColorSettingsPage].
 * They layer on top of the Markdown plugin's generic HTML-tag coloring, so the
 * defaults chain to colors that are visibly distinct from plain markup.
 */
internal object SlidevHighlightColors {

    /** A tag name resolved by the component index (built-in, theme, addon, or local). */
    val COMPONENT_TAG: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "SLIDEV_COMPONENT_TAG",
        DefaultLanguageHighlighterColors.CLASS_NAME,
    )

    /** A `v-`-prefixed directive attribute (`v-click`, `v-mark.red`, ...). */
    val DIRECTIVE_ATTRIBUTE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "SLIDEV_DIRECTIVE_ATTRIBUTE",
        DefaultLanguageHighlighterColors.METADATA,
    )

    /** A `:`-bound or `@`-event attribute (`:scale`, `@click`). */
    val BOUND_ATTRIBUTE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "SLIDEV_BOUND_ATTRIBUTE",
        DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE,
    )
}
