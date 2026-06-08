package dev.slidev.intellij.actions

import dev.slidev.intellij.ui.preview.SlidevPreviewService

/** Opens the running presentation in the system browser, like `slidev.open-in-browser`. */
internal class SlidevOpenInBrowserAction : SlidevPreviewAction() {
    override fun perform(service: SlidevPreviewService) = service.openInBrowser()
}
