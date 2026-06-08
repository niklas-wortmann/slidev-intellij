package dev.slidev.intellij.project

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.vfs.VirtualFile
import dev.slidev.intellij.parser.LoadedSlidevData

/**
 * One registered Slidev project, the counterpart of `SlidevProject` in the VS Code
 * extension. Mutable fields are volatile because they are written from background
 * coroutines and read from the EDT.
 */
class SlidevProjectState(val entryFile: VirtualFile) {

    /** Absolute entry path; VFS paths are slash-normalized, matching [LoadedSlidevData.markdownFiles] keys. */
    val entryPath: String = entryFile.path

    /** The directory of the entry file: loader user root and dev-server working directory. */
    val root: String = entryFile.parent?.path ?: entryPath.substringBeforeLast('/')

    @Volatile
    var data: LoadedSlidevData? = null

    /** Port of the running (or adopted) dev server, null when not running. */
    @Volatile
    var port: Int? = null

    @Volatile
    var processHandler: ProcessHandler? = null

    /** True when an externally started server was adopted instead of spawning one. */
    @Volatile
    var detected: Boolean = false

    /** True when the running server lacks the `slidev:version` meta tag (pre-navigation-API server). */
    @Volatile
    var compatMode: Boolean = false

    val serverRunning: Boolean
        get() = port != null

    val title: String
        get() = data?.headmatter?.get("title") as? String
            ?: data?.slides?.firstOrNull()?.title
            ?: entryFile.name
}
