package dev.slidev.intellij.components

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import dev.slidev.intellij.parser.VueComponentScanner
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.project.SlidevProjectState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The shared component index (plan.md, 13.4): per Slidev project, the components usable
 * as tags in slide content — built-ins, theme/addon package components, and the local
 * `components/` directory, later origins shadowing earlier ones like the directory
 * order of `unplugin-vue-components`. Results are cached per entry and invalidated by
 * `.vue`/`package.json` VFS changes and headmatter `theme:`/`addons:` edits (the cache
 * key carries both); `node_modules` is never touched on the EDT — an EDT caller gets
 * the last snapshot (or built-ins) while a fresh one is computed in the background.
 */
@Service(Service.Level.PROJECT)
class SlidevComponentIndex(private val project: Project, private val scope: CoroutineScope) : Disposable {

    private class Cached(val signature: Signature, val components: Map<String, SlidevComponent>)

    private data class Signature(val theme: String?, val addons: List<String>, val vfsStamp: Long)

    private val cache = ConcurrentHashMap<String, Cached>()

    /** Bumped on any `.vue` / `package.json` VFS change; part of every cache signature. */
    private val vfsStamp = AtomicLong()

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, ComponentFilesListener())
    }

    /** The global Slidev template directives (`v-click`, `v-mark`, ...). */
    fun directives(): List<SlidevDirective> = SlidevBuiltinComponents.directives

    fun directive(name: String): SlidevDirective? = directives().firstOrNull { it.name == name }

    /**
     * The components visible to the deck containing [path] (entry or imported file),
     * keyed by tag name. Built-ins only when [path] is not part of a Slidev project.
     */
    fun componentsFor(path: String): Map<String, SlidevComponent> {
        val state = SlidevProjectService.getInstance(project).stateContaining(path) ?: return builtinsByName
        val signature = signature(state)
        cache[state.entryPath]?.takeIf { it.signature == signature }?.let { return it.components }

        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread && !application.isUnitTestMode) {
            // Stale-while-revalidate: never scan node_modules on the EDT.
            scope.launch {
                val computed = readAction { compute(state) }
                cache[state.entryPath] = Cached(signature(state), computed)
            }
            return cache[state.entryPath]?.components ?: builtinsByName
        }

        val computed = compute(state)
        cache[state.entryPath] = Cached(signature, computed)
        return computed
    }

    fun componentFor(path: String, tagName: String): SlidevComponent? =
        componentsFor(path).componentForTag(tagName)

    // ---------------------------------------------------------------- computation

    private fun signature(state: SlidevProjectState): Signature {
        val headmatter = state.data?.headmatter.orEmpty()
        return Signature(
            theme = headmatter["theme"] as? String,
            addons = (headmatter["addons"] as? List<*>).orEmpty().map { it.toString() },
            vfsStamp = vfsStamp.get(),
        )
    }

    private fun compute(state: SlidevProjectState): Map<String, SlidevComponent> {
        val result = LinkedHashMap(builtinsByName)
        val root = state.entryFile.parent ?: return result
        val headmatter = state.data?.headmatter.orEmpty()

        val theme = headmatter["theme"] as? String
        for (dir in packageComponentDirs(root, SlidevPackageNames.themeCandidates(theme), theme)) {
            scanInto(result, dir, ComponentOrigin.THEME)
        }
        for (addon in (headmatter["addons"] as? List<*>).orEmpty().map { it.toString() }) {
            for (dir in packageComponentDirs(root, SlidevPackageNames.addonCandidates(addon), addon)) {
                scanInto(result, dir, ComponentOrigin.ADDON)
            }
        }
        scanInto(result, root.findChild(COMPONENTS_DIR), ComponentOrigin.LOCAL)
        return result
    }

    /**
     * The `components/` dirs for the package-name [candidates], probing `node_modules`
     * upwards from [root]; a `.`/`/`-prefixed [rawName] resolves as a local path instead.
     */
    private fun packageComponentDirs(root: VirtualFile, candidates: List<String>, rawName: String?): List<VirtualFile> {
        if (rawName != null && SlidevPackageNames.isLocalPath(rawName)) {
            val local = if (rawName.startsWith("/")) {
                root.fileSystem.findFileByPath(rawName)
            }
            else {
                root.findFileByRelativePath(rawName)
            }
            return listOfNotNull(local?.findChild(COMPONENTS_DIR))
        }
        var dir: VirtualFile? = root
        while (dir != null) {
            for (candidate in candidates) {
                val components = dir.findFileByRelativePath("node_modules/$candidate/$COMPONENTS_DIR")
                if (components != null && components.isDirectory) {
                    return listOf(components)
                }
            }
            dir = dir.parent
        }
        return emptyList()
    }

    private fun scanInto(result: MutableMap<String, SlidevComponent>, dir: VirtualFile?, origin: ComponentOrigin) {
        if (dir == null || !dir.isDirectory) {
            return
        }
        VfsUtilCore.visitChildrenRecursively(
            dir,
            object : VirtualFileVisitor<Any>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (file.isDirectory) {
                        return file.name != "node_modules"
                    }
                    if (file.name.endsWith(".vue", ignoreCase = true) && file.length <= MAX_VUE_FILE_SIZE) {
                        val scanned = VueComponentScanner.scan(file.name, VfsUtilCore.loadText(file))
                        result[scanned.name] = SlidevComponent(
                            name = scanned.name,
                            description = null,
                            docsUrl = null,
                            props = scanned.props.map { SlidevComponentProp(it.name, it.type, it.required) },
                            origin = origin,
                            filePath = file.path,
                        )
                    }
                    return true
                }
            },
        )
    }

    /** Invalidates by signature: any `.vue` or `package.json` change bumps the stamp. */
    private inner class ComponentFilesListener : BulkFileListener {
        override fun after(events: List<VFileEvent>) {
            if (events.any(::affectsIndex)) {
                vfsStamp.incrementAndGet()
            }
        }

        private fun affectsIndex(event: VFileEvent): Boolean {
            val path = event.path
            if (path.endsWith(".vue", ignoreCase = true)) {
                return true
            }
            // package.json creations/deletions change node_modules resolution; content
            // changes there don't affect which components exist.
            return path.endsWith("/package.json") && event !is VFileContentChangeEvent
        }
    }

    override fun dispose() {
        cache.clear()
    }

    companion object {
        private const val COMPONENTS_DIR = "components"
        private const val MAX_VUE_FILE_SIZE = 512 * 1024L

        private val builtinsByName: Map<String, SlidevComponent> by lazy {
            SlidevBuiltinComponents.components.associateBy { it.name }
        }

        @JvmStatic
        fun getInstance(project: Project): SlidevComponentIndex = project.service()
    }
}
