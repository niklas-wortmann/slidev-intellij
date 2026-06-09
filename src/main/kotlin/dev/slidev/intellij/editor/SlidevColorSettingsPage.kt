package dev.slidev.intellij.editor

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import dev.slidev.intellij.SlidevBundle
import dev.slidev.intellij.SlidevIcons
import javax.swing.Icon

/**
 * Settings | Editor | Color Scheme | Slidev — the configurable colors applied by
 * [SlidevComponentAnnotator] to component tags and Vue attributes (plan.md, 15.1).
 */
internal class SlidevColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName(): String = SlidevBundle.message("colors.display.name")

    override fun getIcon(): Icon = SlidevIcons.ToolWindow

    override fun getHighlighter(): SyntaxHighlighter = PlainSyntaxHighlighter()

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = mapOf(
        "component" to SlidevHighlightColors.COMPONENT_TAG,
        "directive" to SlidevHighlightColors.DIRECTIVE_ATTRIBUTE,
        "bound" to SlidevHighlightColors.BOUND_ATTRIBUTE,
    )

    override fun getDemoText(): String = """
        # Welcome to Slidev

        <<component>Tweet</component> id="20167698" <bound>:scale</bound>="0.65" />

        <<component>AutoFitText</component> <bound>:max</bound>="200" <directive>v-click</directive>>
          Fit this text
        </<component>AutoFitText</component>>

        <div <directive>v-mark.red</directive> <bound>@click</bound>="next()">plain HTML stays untouched</div>
    """.trimIndent()

    private companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor(SlidevBundle.message("colors.component.tag"), SlidevHighlightColors.COMPONENT_TAG),
            AttributesDescriptor(SlidevBundle.message("colors.directive.attribute"), SlidevHighlightColors.DIRECTIVE_ATTRIBUTE),
            AttributesDescriptor(SlidevBundle.message("colors.bound.attribute"), SlidevHighlightColors.BOUND_ATTRIBUTE),
        )
    }
}
