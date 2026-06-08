package dev.slidev.intellij.ui.preview

import com.google.gson.JsonObject
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.Alarm
import dev.slidev.intellij.core.SlideNavigation
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.project.SlidevProjectState
import dev.slidev.intellij.settings.SlidevSettings
import dev.slidev.intellij.settings.SlidevWorkspaceState

/**
 * Preview navigation state and the editor &harr; preview sync logic, the counterpart of
 * `previewWebview.ts` in the VS Code extension. All state is mutated on the EDT only;
 * the JCEF panel registers itself here so toolbar actions work through this service.
 */
@Service(Service.Level.PROJECT)
class SlidevPreviewService(private val project: Project) : Disposable {

    enum class Mode { SLIDE, OVERVIEW }

    /** Mirror of the Slidev client's `navState`, updated from `update-state` messages. */
    data class NavState(
        val no: Int = 1,
        val clicks: Int = 0,
        val hasNext: Boolean = true,
        val hasPrev: Boolean = false,
    )

    /** Written on the EDT, read from background action `update()` calls. */
    @Volatile
    var navState: NavState = NavState()
        private set

    var panel: SlidevPreviewPanel? = null

    /** clientId of the iframe page we already seeded, see the VS Code `initializedClientId` handshake. */
    private var initializedClientId: String? = null

    /** While now() is before this, editor scrolls are echoes of preview overview scrolls. */
    private var syncEditorToOverviewUntil = 0L

    private val overviewScrollAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    var mode: Mode
        get() = if (SlidevWorkspaceState.getInstance(project).state.previewMode == "overview") Mode.OVERVIEW else Mode.SLIDE
        set(value) {
            val workspace = SlidevWorkspaceState.getInstance(project).state
            val raw = if (value == Mode.OVERVIEW) "overview" else "slide"
            if (workspace.previewMode == raw) {
                return
            }
            workspace.previewMode = raw
            initializedClientId = null
            panel?.reloadWrapper()
        }

    private val syncEnabled: Boolean
        get() = SlidevSettings.getInstance(project).state.previewSync

    private fun activeState(): SlidevProjectState? = SlidevProjectService.getInstance(project).activeState()

    // ------------------------------------------------------------- outgoing messages

    fun postToSlidev(type: String, payload: Map<String, Any?> = emptyMap()) {
        panel?.postMessage(type, payload)
    }

    fun navigateTo(no: Int, clicks: Int = LAST_CLICK) =
        postToSlidev("navigate", mapOf("no" to no, "clicks" to clicks))

    fun nextSlide() = postOperation("nextSlide", listOf(true))
    fun prevSlide() = postOperation("prevSlide", listOf(true))
    fun nextClick() = postOperation("next")
    fun prevClick() = postOperation("prev")

    private fun postOperation(operation: String, args: List<Any?> = emptyList()) =
        postToSlidev("navigate", mapOf("operation" to operation, "args" to args))

    fun refresh() {
        initializedClientId = null
        panel?.reloadWrapper()
    }

    /** Opens the slides in the system browser at the current slide, honoring `routerMode`. */
    fun openInBrowser() {
        val state = activeState() ?: return
        val port = state.port ?: return
        val hash = state.data?.headmatter?.get("routerMode") == "hash"
        BrowserUtil.browse("http://localhost:$port/" + (if (hash) "#/" else "") + navState.no)
    }

    // ------------------------------------------------------------- editor -> preview

    /** Application-wide caret listener, installed by the preview panel with itself as disposable. */
    fun installCaretListener(disposable: Disposable) {
        com.intellij.openapi.editor.EditorFactory.getInstance().eventMulticaster.addCaretListener(
            object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    val editor = event.editor
                    if (editor.project !== project) {
                        return
                    }
                    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
                    onCaretMoved(file.path, event.newPosition.line)
                }
            },
            disposable,
        )
    }

    private fun onCaretMoved(path: String, line: Int) {
        if (!syncEnabled) {
            return
        }
        val state = activeState() ?: return
        if (!state.serverRunning) {
            return
        }
        val data = state.data ?: return
        if (path != state.entryPath && !data.markdownFiles.containsKey(path)) {
            return
        }
        val slide = SlideNavigation.resolvedSlideForLine(data, path, line) ?: return
        when (mode) {
            Mode.SLIDE -> if (slide.no != navState.no) {
                navigateTo(slide.no)
            }

            Mode.OVERVIEW -> {
                if (System.currentTimeMillis() < syncEditorToOverviewUntil) {
                    return
                }
                overviewScrollAlarm.cancelAllRequests()
                overviewScrollAlarm.addRequest(
                    { postToSlidev("overview-scroll", mapOf("no" to slide.no)) },
                    OVERVIEW_SCROLL_DEBOUNCE_MS,
                )
            }
        }
    }

    // ------------------------------------------------------------- preview -> editor

    /** Handles a message relayed by the wrapper page; always called on the EDT. */
    fun onClientMessage(data: JsonObject) {
        when (data.get("type")?.takeIf { it.isJsonPrimitive }?.asString) {
            "update-state" -> onUpdateState(data)
            "overview-scroll" -> onOverviewScroll(data)
            "open-external" -> data.get("url")?.takeIf { it.isJsonPrimitive }?.asString?.let(BrowserUtil::browse)
            "command" -> onCommand(data)
        }
    }

    private fun onUpdateState(data: JsonObject) {
        val nav = data.get("navState")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val no = nav.get("no")?.takeIf { it.isJsonPrimitive }?.asInt ?: return
        val newState = NavState(
            no = no,
            clicks = nav.get("clicks")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
            hasNext = nav.get("hasNext")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            hasPrev = nav.get("hasPrev")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
        )
        val clientId = data.get("clientId")?.takeIf { it.isJsonPrimitive }?.asString

        // A new clientId means the iframe page just loaded: seed it with the editor position
        // instead of yanking the editor to wherever the page starts.
        if (clientId != null && clientId != initializedClientId) {
            initializedClientId = clientId
            navState = newState
            val focused = focusedEditorSlideNo()
            if (syncEnabled && mode == Mode.SLIDE && focused != null && focused != no) {
                navigateTo(focused)
            }
            return
        }

        val changed = no != navState.no
        navState = newState
        if (changed && syncEnabled && mode == Mode.SLIDE && focusedEditorSlideNo() != no) {
            revealSlide(no, focus = false)
        }
    }

    private fun onOverviewScroll(data: JsonObject) {
        val no = data.get("no")?.takeIf { it.isJsonPrimitive }?.asInt ?: return
        if (!syncEnabled) {
            return
        }
        syncEditorToOverviewUntil = System.currentTimeMillis() + OVERVIEW_GUARD_MS
        revealSlide(no, focus = false)
    }

    /** The overview page sends `goto` with the source file and slide index when a slide is clicked. */
    private fun onCommand(data: JsonObject) {
        if (data.get("command")?.takeIf { it.isJsonPrimitive }?.asString != "goto") {
            return
        }
        val args = data.get("args")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        if (args.size() < 2) {
            return
        }
        val filepath = args.get(0)?.takeIf { it.isJsonPrimitive }?.asString ?: return
        val index = args.get(1)?.takeIf { it.isJsonPrimitive }?.asInt ?: return
        val slide = SlidevProjectService.getInstance(project).markdownFor(filepath)?.slides?.getOrNull(index) ?: return
        navigateToSource(filepath, slide.contentStart, focus = true)
    }

    private fun revealSlide(no: Int, focus: Boolean) {
        val slide = activeState()?.data?.slides?.getOrNull(no - 1) ?: return
        navigateToSource(slide.source.filepath, slide.source.contentStart, focus)
    }

    private fun navigateToSource(filepath: String, line: Int, focus: Boolean) {
        val file = LocalFileSystem.getInstance().findFileByPath(filepath) ?: return
        OpenFileDescriptor(project, file, line, 0).navigate(focus)
    }

    private fun focusedEditorSlideNo(): Int? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
        val data = activeState()?.data ?: return null
        return SlideNavigation.resolvedSlideForLine(data, file.path, editor.caretModel.logicalPosition.line)?.no
    }

    override fun dispose() {}

    companion object {
        /** "Jump to the last click of the slide", the value the VS Code extension posts. */
        const val LAST_CLICK = 999999

        private const val OVERVIEW_GUARD_MS = 300L
        private const val OVERVIEW_SCROLL_DEBOUNCE_MS = 50

        @JvmStatic
        fun getInstance(project: Project): SlidevPreviewService = project.service()
    }
}
