package dev.slidev.intellij.actions

import dev.slidev.intellij.ui.preview.SlidevPreviewService

/** Navigates the preview to the previous click, like `slidev.preview-prev-click`. */
internal class SlidevPrevClickAction : SlidevPreviewAction() {
    override val hiddenInCompatMode: Boolean = true
    override fun extraEnabled(service: SlidevPreviewService): Boolean = service.navState.hasPrev
    override fun perform(service: SlidevPreviewService) = service.prevClick()
}
