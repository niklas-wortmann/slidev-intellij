package dev.slidev.intellij.actions

import dev.slidev.intellij.ui.preview.SlidevPreviewService

/** Reloads the preview wrapper page, like `slidev.refresh-preview`. */
internal class SlidevRefreshPreviewAction : SlidevPreviewAction() {
    override fun perform(service: SlidevPreviewService) = service.refresh()
}
