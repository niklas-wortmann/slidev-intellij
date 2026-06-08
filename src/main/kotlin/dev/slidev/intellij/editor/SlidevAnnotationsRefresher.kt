package dev.slidev.intellij.editor

import com.intellij.openapi.editor.EditorFactory
import dev.slidev.intellij.project.SlidevListener
import dev.slidev.intellij.project.SlidevProjectState

/**
 * Repaints all editors when slide data or settings change, so the
 * [SlidevSlideLinePainter] extensions reflect the latest state. Registered
 * declaratively in plugin.xml; events always arrive on the EDT.
 */
internal class SlidevAnnotationsRefresher : SlidevListener {

    override fun dataReloaded(state: SlidevProjectState) = refresh()

    override fun settingsChanged() = refresh()

    private fun refresh() = EditorFactory.getInstance().refreshAllEditors()
}
