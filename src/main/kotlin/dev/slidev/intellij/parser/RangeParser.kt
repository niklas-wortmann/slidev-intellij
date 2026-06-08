package dev.slidev.intellij.parser

/**
 * Port of `parseRangeString` from `@slidev/parser/utils`.
 * `1,3-5,8` => [1, 3, 4, 5, 8]
 */
object RangeParser {
    fun parseRangeString(total: Int, rangeStr: String?): List<Int> {
        if (rangeStr.isNullOrEmpty() || rangeStr == "all" || rangeStr == "*") {
            return (1..total).toList()
        }

        if (rangeStr == "none") {
            return emptyList()
        }

        val indexes = mutableListOf<Int>()
        for (part in rangeStr.split(Regex("[,;]"))) {
            if (!part.contains('-')) {
                part.trim().toIntOrNull()?.let { indexes.add(it) }
            }
            else {
                val pieces = part.split("-", limit = 2)
                val start = pieces[0].trim().toIntOrNull() ?: continue
                val end = pieces.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: total
                indexes.addAll(start..end)
            }
        }

        return indexes.distinct().filter { it <= total }.sorted()
    }
}
