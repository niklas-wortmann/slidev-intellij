package dev.slidev.intellij.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiFile
import dev.slidev.intellij.project.SlidevProjectService
import dev.slidev.intellij.schema.SlidevSchemas
import dev.slidev.intellij.SlidevIcons

/**
 * Schema-driven completion inside classic `---` frontmatter blocks: top-level keys
 * with type and doc text, and enum values (`BuiltinLayouts`, `BuiltinSlideTransition`)
 * after `key:`. The IntelliJ counterpart of the YAML-schema completion of the VS Code
 * language server; implemented directly because mid-document frontmatter blocks are
 * plain Markdown paragraphs and not language-injection hosts (see plan.md, Phase 8).
 */
internal class SlidevFrontmatterCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        // Completion in the headmatter runs inside the injected YAML fragment; map back to
        // the host markdown file so block/line coordinates match the frontmatter context.
        val (hostFile, document) = hostLocation(parameters.originalFile, parameters.offset) ?: return
        val context = frontmatterContext(parameters.originalFile, parameters.offset) ?: return
        val schema = SlidevSchemas.forSlide(context.block.slideIndex)
        val lines = document.charsSequence.lines()

        when (context) {
            is SlidevFrontmatterBlocks.Context.Key -> {
                val present = SlidevFrontmatterBlocks.presentKeys(lines, context.block)
                val matcher = result.withPrefixMatcher(context.prefix)
                for (property in schema.properties.values) {
                    if (property.name in present) {
                        continue
                    }
                    matcher.addElement(
                        // The property is the lookup object so the documentation provider can
                        // render quick docs for the selected item without re-locating the caret.
                        LookupElementBuilder.create(property, property.name)
                            .withIcon(SlidevIcons.ToolWindow)
                            .withTypeText(property.typeText, true)
                            .withTailText(property.description?.lineSequence()?.firstOrNull()?.let { "  $it" }, true)
                            .withInsertHandler { insertion, _ ->
                                val tail = insertion.document.charsSequence
                                if (insertion.tailOffset >= tail.length || tail[insertion.tailOffset] != ':') {
                                    insertion.document.insertString(insertion.tailOffset, ": ")
                                    insertion.editor.caretModel.moveToOffset(insertion.tailOffset + 2)
                                }
                            },
                    )
                }
            }

            is SlidevFrontmatterBlocks.Context.Value -> {
                if (context.key == "src") {
                    // Slide-import path completion; `src` is a plain string property in
                    // both schemas, so the enum lookup below would offer nothing for it.
                    addSrcPathItems(hostFile, context, result)
                    return
                }
                val property = schema.properties[context.key] ?: return
                val matcher = result.withPrefixMatcher(context.prefix)
                for (value in property.enumValues) {
                    matcher.addElement(
                        LookupElementBuilder.create(property, value)
                            .withIcon(SlidevIcons.ToolWindow)
                            .withTypeText(context.key, true),
                    )
                }
            }
        }
    }

    /**
     * Offers the deck's markdown files as `src:` import values: root-relative items when
     * the typed prefix starts with `/`, importer-relative items otherwise. Containment
     * matching ([PlainPrefixMatcher]) so `pages/in` or a bare file name matches
     * `./pages/intro.md`.
     */
    private fun addSrcPathItems(
        hostFile: PsiFile,
        context: SlidevFrontmatterBlocks.Context.Value,
        result: CompletionResultSet,
    ) {
        // A `#` in the prefix means a page range is being typed, not a path.
        val prefix = SlidevSrcPathCompletion.parsePrefix(context.prefix) ?: return
        val virtualFile = hostFile.viewProvider.virtualFile
        val state = SlidevProjectService.getInstance(hostFile.project).stateContaining(virtualFile.path) ?: return
        // Walk from the entry file's parent so the temp file system used in tests works too.
        val rootDir = state.entryFile.parent ?: return
        val filePaths = mutableListOf<String>()
        VfsUtilCore.visitChildrenRecursively(
            rootDir,
            object : VirtualFileVisitor<Any>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (file.isDirectory) {
                        return !SlidevSrcPathCompletion.excludedDirectory(file.name)
                    }
                    if (file.name.endsWith(".md", ignoreCase = true)) {
                        filePaths.add(file.path)
                    }
                    return true
                }
            },
        )
        val icon = FileTypeManager.getInstance().getFileTypeByFileName("a.md").icon
        val matcher = result.withPrefixMatcher(PlainPrefixMatcher(prefix.matchText))
        for (candidate in SlidevSrcPathCompletion.candidates(filePaths, virtualFile.path, state.root, prefix.rootRelative)) {
            matcher.addElement(
                LookupElementBuilder.create(candidate.text)
                    .withIcon(icon)
                    .withTypeText(candidate.typeText, true)
                    .withInsertHandler { insertion, _ ->
                        // Close the YAML quote the user opened before the path.
                        val text = insertion.document.charsSequence
                        val quote = insertion.startOffset.takeIf { it > 0 }?.let { text[it - 1] }
                        if ((quote == '"' || quote == '\'') &&
                            (insertion.tailOffset >= text.length || text[insertion.tailOffset] != quote)
                        ) {
                            insertion.document.insertString(insertion.tailOffset, quote.toString())
                        }
                    },
            )
        }
    }

    companion object {

        /** The frontmatter caret context, or null when [file] is not part of a Slidev project. */
        internal fun frontmatterContext(file: PsiFile, offset: Int): SlidevFrontmatterBlocks.Context? {
            val (hostFile, hostDocument, hostOffset) = hostLocation(file, offset) ?: return null
            val virtualFile = hostFile.viewProvider.virtualFile
            if (!virtualFile.name.endsWith(".md", ignoreCase = true)) {
                return null
            }
            SlidevProjectService.getInstance(hostFile.project).stateContaining(virtualFile.path) ?: return null
            val text = hostDocument.charsSequence.toString()
            val blocks = SlidevFrontmatterBlocks.blocks(text, virtualFile.path)
            if (blocks.isEmpty()) {
                return null
            }
            val line = hostDocument.getLineNumber(hostOffset.coerceIn(0, text.length))
            val column = hostOffset - hostDocument.getLineStartOffset(line)
            return SlidevFrontmatterBlocks.contextAt(text.lines(), blocks, line, column)
        }

        /**
         * Maps a possibly injected (file, offset) — the headmatter is a YAML injection host —
         * to the host markdown file, its document, and the corresponding host offset.
         */
        internal fun hostLocation(file: PsiFile, offset: Int): Triple<PsiFile, Document, Int>? {
            val manager = InjectedLanguageManager.getInstance(file.project)
            val hostFile: PsiFile
            val hostOffset: Int
            if (manager.isInjectedFragment(file)) {
                hostFile = manager.getTopLevelFile(file) ?: return null
                hostOffset = manager.injectedToHost(file, offset)
            }
            else {
                hostFile = file
                hostOffset = offset
            }
            val document = hostFile.viewProvider.document ?: return null
            return Triple(hostFile, document, hostOffset)
        }
    }
}
