package dev.slidev.intellij.editor

import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup

/**
 * Keeps the component completion popup open while a Vue attribute or directive is being
 * typed: `-` (`v-click`), `:` (`:size`), `@` and `.` are not Java identifier parts, so
 * the platform's `DefaultCharFilter` would hide the lookup on them (see
 * [SlidevSrcPathCharFilter] for the same workaround on `src:` paths).
 */
internal class SlidevComponentTagCharFilter : CharFilter() {

    override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup): Result? {
        if (c != '-' && c != ':' && c != '@' && c != '.') {
            return null
        }
        val file = lookup.psiFile ?: return null
        val location = SlidevComponentCompletionContributor.slideLocation(file, lookup.editor.caretModel.offset)
            ?: return null
        SlidevSlideTags.contextAt(location.text, location.path, location.offset) ?: return null
        return Result.ADD_TO_PREFIX
    }
}
