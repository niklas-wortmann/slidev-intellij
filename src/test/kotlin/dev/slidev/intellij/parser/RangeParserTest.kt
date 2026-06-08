package dev.slidev.intellij.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeParserTest {

    @Test
    fun `null and all and star return full range`() {
        assertEquals(listOf(1, 2, 3), RangeParser.parseRangeString(3, null))
        assertEquals(listOf(1, 2, 3), RangeParser.parseRangeString(3, "all"))
        assertEquals(listOf(1, 2, 3), RangeParser.parseRangeString(3, "*"))
    }

    @Test
    fun `none returns empty`() {
        assertEquals(emptyList<Int>(), RangeParser.parseRangeString(3, "none"))
    }

    @Test
    fun `single numbers and comma lists`() {
        assertEquals(listOf(2), RangeParser.parseRangeString(5, "2"))
        assertEquals(listOf(1, 3, 5), RangeParser.parseRangeString(5, "1,3,5"))
        assertEquals(listOf(1, 3), RangeParser.parseRangeString(5, "1;3"))
    }

    @Test
    fun `ranges expand inclusively`() {
        assertEquals(listOf(1, 3, 4, 5, 8), RangeParser.parseRangeString(10, "1,3-5,8"))
    }

    @Test
    fun `open-ended range runs to total`() {
        assertEquals(listOf(3, 4, 5), RangeParser.parseRangeString(5, "3-"))
    }

    @Test
    fun `out of bounds values are dropped and result is sorted unique`() {
        assertEquals(listOf(1, 2, 3), RangeParser.parseRangeString(3, "3,1,2,2,9"))
    }
}
