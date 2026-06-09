package dev.slidev.intellij.editor

import dev.slidev.intellij.project.SlidevProjectService
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

/**
 * The built-in markdown preview crashes on Slidev decks: slides routinely contain Vue
 * template syntax in inline HTML (`<span @click="...">`, `:class="..."`), and the preview's
 * incremental-DOM renderer applies attributes via `Element.setAttribute`, which throws
 * `InvalidCharacterError` for such names and aborts the whole preview update. This extension
 * injects [SCRIPT_NAME], which drops invalid attribute names instead, keeping the rest of
 * the preview alive. It is installed only for files of a registered Slidev project.
 */
internal class SlidevPreviewVueAttributesExtension : MarkdownBrowserPreviewExtension, ResourceProvider {

    override val priority: MarkdownBrowserPreviewExtension.Priority
        get() = MarkdownBrowserPreviewExtension.Priority.BEFORE_ALL

    override val scripts: List<String> = listOf(SCRIPT_NAME)

    override val resourceProvider: ResourceProvider = this

    override fun canProvide(resourceName: String): Boolean = resourceName == SCRIPT_NAME

    override fun loadResource(resourceName: String): ResourceProvider.Resource? =
        ResourceProvider.loadInternalResource(SlidevPreviewVueAttributesExtension::class.java, "/$SCRIPT_NAME")

    override fun dispose() {}

    internal class Provider : MarkdownBrowserPreviewExtension.Provider {
        override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension? {
            val project = panel.project ?: return null
            val file = panel.virtualFile ?: return null
            SlidevProjectService.getInstance(project).stateContaining(file.path) ?: return null
            return SlidevPreviewVueAttributesExtension()
        }
    }

    private companion object {
        const val SCRIPT_NAME = "slidev/vueAttributesPatch.js"
    }
}
