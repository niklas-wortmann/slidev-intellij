package dev.slidev.intellij.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import dev.slidev.intellij.core.SlidevGlobs
import dev.slidev.intellij.parser.SlidevDataLoader
import dev.slidev.intellij.parser.SlidevMarkdown
import dev.slidev.intellij.server.SlidevServerManager
import dev.slidev.intellij.settings.SlidevSettings
import dev.slidev.intellij.settings.SlidevWorkspaceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Central hub of the plugin: the registry of detected/registered Slidev projects,
 * the active entry, and the debounced re-parsing pipeline. The counterpart of
 * `projects.ts` in the VS Code extension. UI components listen via [SlidevListener.TOPIC].
 */
@Service(Service.Level.PROJECT)
class SlidevProjectService(private val project: Project, private val scope: CoroutineScope) : Disposable {

    private val lock = Any()
    private val states = LinkedHashMap<String, SlidevProjectState>()
    private val reloadJobs = ConcurrentHashMap<String, Job>()
    private val initialized = AtomicBoolean()

    @Volatile
    var activeEntryPath: String? = null
        private set

    /** Called once from the startup activity: restores persisted entries and wires listeners. */
    fun initialize() {
        if (!initialized.compareAndSet(false, true)) {
            return
        }

        val workspace = SlidevWorkspaceState.getInstance(project).state
        synchronized(lock) {
            for (path in workspace.entries) {
                val file = LocalFileSystem.getInstance().findFileByPath(path)
                if (file != null && file.isValid) {
                    states.getOrPut(file.path) { SlidevProjectState(file) }
                }
            }
            activeEntryPath = workspace.activeEntry?.takeIf { it in states }
        }

        val multicaster = EditorFactory.getInstance().eventMulticaster
        multicaster.addDocumentListener(ReloadOnEditListener(), this)
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, EntryFilesListener())

        rescan()
    }

    // ---------------------------------------------------------------- registry

    fun projects(): List<SlidevProjectState> = synchronized(lock) { states.values.toList() }

    fun activeState(): SlidevProjectState? = synchronized(lock) { activeEntryPath?.let(states::get) }

    fun stateFor(entryPath: String): SlidevProjectState? = synchronized(lock) { states[entryPath] }

    /** The project whose resolved deck contains [path] (entry or imported markdown file). */
    fun stateContaining(path: String): SlidevProjectState? = synchronized(lock) {
        states.values.firstOrNull { it.entryPath == path || it.data?.markdownFiles?.containsKey(path) == true }
    }

    /** The parsed markdown of [path] in any registered project, for folding and annotations. */
    fun markdownFor(path: String): SlidevMarkdown? = synchronized(lock) {
        states.values.firstNotNullOfOrNull { it.data?.markdownFiles?.get(path) }
    }

    fun addEntry(file: VirtualFile) {
        val state = synchronized(lock) {
            val state = states.getOrPut(file.path) { SlidevProjectState(file) }
            if (activeEntryPath == null) {
                activeEntryPath = file.path
            }
            persistLocked()
            state
        }
        publishProjectsChanged()
        scheduleReload(state)
    }

    fun removeEntry(entryPath: String) {
        val removed = synchronized(lock) {
            val removed = states.remove(entryPath) ?: return
            if (activeEntryPath == entryPath) {
                activeEntryPath = null
                pickActiveLocked()
            }
            persistLocked()
            removed
        }
        reloadJobs.remove(entryPath)?.cancel()
        stopServer(removed)
        publishProjectsChanged()
    }

    fun setActive(entryPath: String) {
        synchronized(lock) {
            if (entryPath !in states || activeEntryPath == entryPath) {
                return
            }
            activeEntryPath = entryPath
            persistLocked()
        }
        publishProjectsChanged()
    }

    /** Rescans the workspace for glob-matching entries, keeping manually added ones. */
    fun rescan() {
        scope.launch {
            val globs = currentGlobs()
            val found = readAction { SlidevWorkspaceScanner.scan(project, globs) }
            mergeScanResults(found)
            projects().forEach(::scheduleReload)
        }
    }

    fun onSettingsChanged(globsChanged: Boolean) {
        publish { it.settingsChanged() }
        if (globsChanged) {
            rescan()
        }
    }

    private fun currentGlobs(): SlidevGlobs {
        val settings = SlidevSettings.getInstance(project).state
        return SlidevGlobs(settings.include, settings.exclude)
    }

    private fun mergeScanResults(found: List<VirtualFile>) {
        val dead: List<SlidevProjectState>
        synchronized(lock) {
            dead = states.values.filter { !it.entryFile.isValid }
            dead.forEach { states.remove(it.entryPath) }
            for (file in found) {
                states.getOrPut(file.path) { SlidevProjectState(file) }
            }
            if (activeEntryPath !in states) {
                activeEntryPath = null
            }
            pickActiveLocked()
            persistLocked()
        }
        dead.forEach(::stopServer)
        publishProjectsChanged()
    }

    /** Prefers the shallowest `slides.md`, then the shallowest entry overall, like VS Code. */
    private fun pickActiveLocked() {
        if (activeEntryPath != null || states.isEmpty()) {
            return
        }
        val byDepth = compareBy<SlidevProjectState> { it.entryPath.count { c -> c == '/' } }
        activeEntryPath = (
            states.values.filter { it.entryFile.name == "slides.md" }.minWithOrNull(byDepth)
                ?: states.values.minWithOrNull(byDepth)
            )?.entryPath
    }

    private fun persistLocked() {
        val workspace = SlidevWorkspaceState.getInstance(project).state
        workspace.entries = states.keys.toMutableList()
        workspace.activeEntry = activeEntryPath
    }

    // ---------------------------------------------------------------- reloading

    /** Debounced re-parse of [state], like the 200 ms document watcher in VS Code. */
    fun scheduleReload(state: SlidevProjectState) {
        reloadJobs.compute(state.entryPath) { _, previous ->
            previous?.cancel()
            scope.launch {
                delay(RELOAD_DEBOUNCE_MS)
                state.data = readAction { loadData(state) }
                publish { it.dataReloaded(state) }
            }
        }
    }

    private fun loadData(state: SlidevProjectState) =
        SlidevDataLoader.load(state.root, state.entryPath, IdeFileTextProvider(state.entryFile.fileSystem))

    private inner class ReloadOnEditListener : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
            if (!file.name.endsWith(".md", ignoreCase = true)) {
                return
            }
            val path = file.path
            synchronized(lock) {
                states.values.filter { it.entryPath == path || it.data?.markdownFiles?.containsKey(path) == true }
            }.forEach(::scheduleReload)
        }
    }

    /** Re-scans when glob-matching markdown files appear, disappear, or get renamed. */
    private inner class EntryFilesListener : BulkFileListener {
        override fun after(events: List<VFileEvent>) {
            if (events.any(::affectsRegistry)) {
                rescan()
            }
        }

        private fun affectsRegistry(event: VFileEvent): Boolean {
            val relevant = when (event) {
                is VFileCreateEvent, is VFileDeleteEvent, is VFileMoveEvent -> true
                is VFilePropertyChangeEvent -> event.propertyName == VirtualFile.PROP_NAME
                else -> false
            }
            if (!relevant || !event.path.endsWith(".md", ignoreCase = true)) {
                return false
            }
            val isKnown = synchronized(lock) { event.path in states }
            if (isKnown) {
                return true
            }
            val file = event.file ?: return false
            return SlidevWorkspaceScanner.matches(ProjectFileIndex.getInstance(project), currentGlobs(), file)
        }
    }

    // ---------------------------------------------------------------- server facade

    fun startServer(state: SlidevProjectState) {
        SlidevServerManager.getInstance(project).start(state)
    }

    fun stopServer(state: SlidevProjectState) {
        SlidevServerManager.getInstance(project).stop(state)
    }

    // ---------------------------------------------------------------- events

    private fun publishProjectsChanged() = publish { it.projectsChanged() }

    internal fun publish(block: (SlidevListener) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                block(project.messageBus.syncPublisher(SlidevListener.TOPIC))
            }
        }
    }

    override fun dispose() {
        projects().forEach { it.processHandler?.destroyProcess() }
    }

    // ---------------------------------------------------------------- tests

    /** Synchronous scan + reload for tests; runs on the calling (EDT) thread. */
    @TestOnly
    fun rescanSync() {
        val found = ReadAction.compute<List<VirtualFile>, RuntimeException> {
            SlidevWorkspaceScanner.scan(project, currentGlobs())
        }
        mergeScanResults(found)
        projects().forEach(::reloadSync)
    }

    @TestOnly
    fun reloadSync(state: SlidevProjectState) {
        state.data = ReadAction.compute<dev.slidev.intellij.parser.LoadedSlidevData, RuntimeException> {
            loadData(state)
        }
    }

    companion object {
        private const val RELOAD_DEBOUNCE_MS = 200L

        @JvmStatic
        fun getInstance(project: Project): SlidevProjectService = project.service()
    }
}
