package dev.slidev.intellij.mcp

import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import dev.slidev.intellij.core.SlideNavigation
import dev.slidev.intellij.parser.SlidevStringifier
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.project.SlidevProjectState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Slidev tools for the bundled MCP server, the counterpart of `lmTools.ts` in the
 * VS Code extension. Pure delegations to [SlidevProjectService]; registered through
 * the optional `com.intellij.mcpServer` dependency (see slidev-mcp.xml).
 */
@Suppress("FunctionName")
class SlidevMcpToolset : McpToolset {

    @McpTool
    @McpDescription("Get information about the active Slidev project and the slide currently being edited")
    suspend fun slidev_get_active_slide(): String {
        val project = coroutineContext.project
        val state = SlidevProjectService.getInstance(project).activeState()
            ?: throw McpExpectedError("No active Slidev project.")
        val (editedPath, editedSlide) = withContext(Dispatchers.EDT) { focusedSlide(project, state) }
        return listOf(
            "Entry file of the active project: ${state.entryPath}",
            "Root directory: ${state.root}",
            "Preview server port: ${state.port ?: "Not running"}",
            "Slide count: ${state.data?.slides?.size ?: 0}",
            "Focused slide number (from 1): ${editedSlide?.no ?: "None"}",
            "Editing file: ${editedPath ?: "Not editing"}",
            "Index of the editing slide in the file (from 0): ${editedSlide?.source?.index ?: "N/A"}",
        ).joinToString("\n") { "- $it" }
    }

    @McpTool
    @McpDescription("Get the markdown source of one slide of a Slidev project by its 1-based slide number")
    suspend fun slidev_get_slide_content(
        @McpDescription("Path of the project entry file; pass an empty string for the active project")
        entrySlidePath: String,
        @McpDescription("1-based slide number")
        slideNo: Int,
    ): String {
        val state = resolveProject(coroutineContext.project, entrySlidePath)
        val slides = state.data?.slides.orEmpty()
        val slide = slides.getOrNull(slideNo - 1)
            ?: throw McpExpectedError("Slide number $slideNo is out of range. Valid range: 1..${slides.size}.")
        return "Slide #$slideNo of entry ${state.entryPath} (source file ${slide.source.filepath}):\n\n" +
            SlidevStringifier.stringifySlide(slide.source.index, slide.source)
    }

    @McpTool
    @McpDescription("Get all slide titles of a Slidev project")
    suspend fun slidev_get_all_slide_titles(
        @McpDescription("Path of the project entry file; pass an empty string for the active project")
        entrySlidePath: String,
    ): String {
        val state = resolveProject(coroutineContext.project, entrySlidePath)
        return state.data?.slides.orEmpty()
            .joinToString("\n") { "- #${it.no}: ${it.title ?: "(Untitled)"}" }
            .ifEmpty { "No slides." }
    }

    @McpTool
    @McpDescription("Find the 1-based slide number of a slide by its exact title")
    suspend fun slidev_find_slide_no_by_title(
        @McpDescription("Path of the project entry file; pass an empty string for the active project")
        entrySlidePath: String,
        @McpDescription("Exact slide title to look for")
        title: String,
    ): String {
        val state = resolveProject(coroutineContext.project, entrySlidePath)
        val slide = state.data?.slides.orEmpty().firstOrNull { it.title == title }
            ?: throw McpExpectedError("No slide with title \"$title\" found in ${state.entryPath}.")
        return "- Title: $title\n- Slide number (from 1): ${slide.no}"
    }

    @McpTool
    @McpDescription("List the entry files of all loaded Slidev projects")
    suspend fun slidev_list_entries(): String {
        val entries = SlidevProjectService.getInstance(coroutineContext.project).projects()
        if (entries.isEmpty()) {
            return "No loaded Slidev project entries."
        }
        return entries.joinToString("\n") { "- ${it.entryPath}" }
    }

    @McpTool
    @McpDescription("Get the preview dev-server port of a Slidev project")
    suspend fun slidev_get_preview_port(
        @McpDescription("Path of the project entry file; pass an empty string for the active project")
        entrySlidePath: String,
    ): String {
        val state = resolveProject(coroutineContext.project, entrySlidePath)
        return "- Project entry: ${state.entryPath}\n- Preview port: ${state.port ?: "Not running"}"
    }

    @McpTool
    @McpDescription("Set the active Slidev project entry")
    suspend fun slidev_choose_entry(
        @McpDescription("Path of the project entry file to activate")
        entrySlidePath: String,
    ): String {
        if (entrySlidePath.isBlank()) {
            throw McpExpectedError("entrySlidePath must not be empty.")
        }
        val project = coroutineContext.project
        val state = resolveProject(project, entrySlidePath)
        SlidevProjectService.getInstance(project).setActive(state.entryPath)
        return "- Active Slidev entry switched to: ${state.entryPath}"
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Resolves [entrySlidePath] to a registered project: the active one for the
     * `$ACTIVE_SLIDE_ENTRY`/blank placeholder, then an exact path match, then a unique
     * slash-normalized substring match — like `getProjectFromPath` in `lmTools.ts`.
     */
    private fun resolveProject(project: Project, entrySlidePath: String): SlidevProjectState {
        val service = SlidevProjectService.getInstance(project)
        if (entrySlidePath.isBlank() || entrySlidePath == ACTIVE_ENTRY_PLACEHOLDER) {
            return service.activeState() ?: throw McpExpectedError("No active Slidev project.")
        }
        val normalized = entrySlidePath.replace('\\', '/')
        service.stateFor(normalized)?.let { return it }
        val matches = service.projects().filter { it.entryPath.contains(normalized) }
        return when (matches.size) {
            1 -> matches.single()
            0 -> throw McpExpectedError(
                "No Slidev project matching \"$entrySlidePath\". Loaded entries:\n" +
                    service.projects().joinToString("\n") { "- ${it.entryPath}" },
            )

            else -> throw McpExpectedError(
                "Multiple Slidev projects match \"$entrySlidePath\":\n" +
                    matches.joinToString("\n") { "- ${it.entryPath}" },
            )
        }
    }

    /** The file and resolved slide under the caret of the focused editor; EDT only. */
    private fun focusedSlide(
        project: Project,
        state: SlidevProjectState,
    ): Pair<String?, dev.slidev.intellij.parser.ResolvedSlide?> {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null to null
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null to null
        val data = state.data ?: return file.path to null
        val slide = SlideNavigation.resolvedSlideForLine(data, file.path, editor.caretModel.logicalPosition.line)
        return file.path to slide
    }

    companion object {
        private const val ACTIVE_ENTRY_PLACEHOLDER = "\$ACTIVE_SLIDE_ENTRY"
    }
}
