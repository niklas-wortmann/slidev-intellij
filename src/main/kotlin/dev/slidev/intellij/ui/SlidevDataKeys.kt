package dev.slidev.intellij.ui

import com.intellij.openapi.actionSystem.DataKey
import dev.slidev.intellij.project.SlidevProjectState

/** [DataKey]s exposed by the Slidev tool-window panels for action enablement and execution. */
object SlidevDataKeys {
    @JvmField
    val PROJECT_STATE: DataKey<SlidevProjectState> = DataKey.create("slidev.projectState")
}
