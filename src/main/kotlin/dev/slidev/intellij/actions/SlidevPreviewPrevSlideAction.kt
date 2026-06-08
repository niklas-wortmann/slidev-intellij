package dev.slidev.intellij.actions

import dev.slidev.intellij.ui.preview.SlidevPreviewService

/** Navigates the preview to the previous slide, like `slidev.preview-prev-slide`. */
internal class SlidevPreviewPrevSlideAction : SlidevPreviewAction() {
    override fun extraEnabled(service: SlidevPreviewService): Boolean = service.navState.hasPrev
    override fun perform(service: SlidevPreviewService) = service.prevSlide()
}
