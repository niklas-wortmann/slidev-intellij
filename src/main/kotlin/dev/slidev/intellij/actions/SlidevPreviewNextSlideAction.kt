package dev.slidev.intellij.actions

import dev.slidev.intellij.ui.preview.SlidevPreviewService

/** Navigates the preview to the next slide, like `slidev.preview-next-slide`. */
internal class SlidevPreviewNextSlideAction : SlidevPreviewAction() {
    override val hiddenInCompatMode: Boolean = true
    override fun extraEnabled(service: SlidevPreviewService): Boolean = service.navState.hasNext
    override fun perform(service: SlidevPreviewService) = service.nextSlide()
}
