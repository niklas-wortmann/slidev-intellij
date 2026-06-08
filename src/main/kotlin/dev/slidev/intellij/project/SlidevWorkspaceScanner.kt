package dev.slidev.intellij.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import dev.slidev.intellij.core.SlidevGlobs

/**
 * Finds Slidev entry files in the project content, the counterpart of
 * `findPossibleEntries` in the VS Code extension. Must run under a read action.
 */
object SlidevWorkspaceScanner {

    fun scan(project: Project, globs: SlidevGlobs): List<VirtualFile> {
        val index = ProjectFileIndex.getInstance(project)
        val result = mutableListOf<VirtualFile>()
        index.iterateContent { file ->
            if (matches(index, globs, file)) {
                result.add(file)
            }
            true
        }
        return result
    }

    /** Whether [file] is a glob-matching markdown file inside the project content. */
    fun matches(index: ProjectFileIndex, globs: SlidevGlobs, file: VirtualFile): Boolean {
        if (file.isDirectory || !file.name.endsWith(".md", ignoreCase = true)) {
            return false
        }
        val root = index.getContentRootForFile(file) ?: return false
        val relative = VfsUtilCore.getRelativePath(file, root) ?: return false
        return globs.matches(relative)
    }
}
