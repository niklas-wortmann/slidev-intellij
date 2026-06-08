package dev.slidev.intellij.actions

import dev.slidev.intellij.core.SlideNavigation
import dev.slidev.intellij.parser.SlidevMarkdown

/** Moves the caret to the content start of the previous slide, like `slidev.prev`. */
internal class SlidevPrevSlideAction : SlidevEditorSlideAction() {
    override fun targetLine(md: SlidevMarkdown, line: Int): Int? =
        SlideNavigation.prevSlideContentStart(md, line)
}
