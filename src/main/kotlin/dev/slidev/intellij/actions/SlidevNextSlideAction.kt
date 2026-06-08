package dev.slidev.intellij.actions

import dev.slidev.intellij.core.SlideNavigation
import dev.slidev.intellij.parser.SlidevMarkdown

/** Moves the caret to the content start of the next slide, like `slidev.next`. */
internal class SlidevNextSlideAction : SlidevEditorSlideAction() {
    override fun targetLine(md: SlidevMarkdown, line: Int): Int? =
        SlideNavigation.nextSlideContentStart(md, line)
}
