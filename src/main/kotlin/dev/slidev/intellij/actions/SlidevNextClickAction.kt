package dev.slidev.intellij.actions

import dev.slidev.intellij.ui.preview.SlidevPreviewService

/** Navigates the preview to the next click, like `slidev.preview-next-click`. */
internal class SlidevNextClickAction : SlidevPreviewAction() {
    override fun extraEnabled(service: SlidevPreviewService): Boolean = service.navState.hasNext
    override fun perform(service: SlidevPreviewService) = service.nextClick()
}
