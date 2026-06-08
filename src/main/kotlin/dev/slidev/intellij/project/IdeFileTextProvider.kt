package dev.slidev.intellij.project

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileSystem
import dev.slidev.intellij.parser.FileTextProvider

/**
 * [FileTextProvider] backed by the IDE: open Documents win (so unsaved edits are
 * parsed), with a VFS read as fallback. Must be called under a read action.
 * The file system is taken from the entry file so tests on the temp FS work too.
 */
class IdeFileTextProvider(private val fileSystem: VirtualFileSystem) : FileTextProvider {

    override fun text(path: String): String? {
        val file = fileSystem.findFileByPath(path) ?: return null
        if (!file.isValid || file.isDirectory) {
            return null
        }
        val document = FileDocumentManager.getInstance().getDocument(file)
        return document?.text ?: runCatching { VfsUtilCore.loadText(file) }.getOrNull()
    }
}
