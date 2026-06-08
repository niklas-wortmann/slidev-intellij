package dev.slidev.intellij.project

import com.intellij.util.messages.Topic

/**
 * Project-level events published by [SlidevProjectService] (always on the EDT).
 * UI components subscribe via `project.messageBus.connect(disposable)`.
 */
interface SlidevListener {
    /** The project registry or the active entry changed. */
    fun projectsChanged() {}

    /** [state]'s slide data was re-parsed. */
    fun dataReloaded(state: SlidevProjectState) {}

    /** [state]'s dev server started, stopped, or changed port. */
    fun serverChanged(state: SlidevProjectState) {}

    /** Plugin settings were applied. */
    fun settingsChanged() {}

    companion object {
        @Topic.ProjectLevel
        @JvmField
        val TOPIC: Topic<SlidevListener> = Topic.create("Slidev", SlidevListener::class.java)
    }
}
