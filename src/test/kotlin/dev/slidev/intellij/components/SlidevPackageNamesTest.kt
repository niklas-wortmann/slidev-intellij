package dev.slidev.intellij.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidevPackageNamesTest {

    @Test
    fun `missing theme falls back to the default theme packages`() {
        assertEquals(listOf("@slidev/theme-default", "slidev-theme-default"), SlidevPackageNames.themeCandidates(null))
        assertEquals(listOf("@slidev/theme-default", "slidev-theme-default"), SlidevPackageNames.themeCandidates("  "))
    }

    @Test
    fun `bare theme name probes official then community package`() {
        assertEquals(listOf("@slidev/theme-seriph", "slidev-theme-seriph"), SlidevPackageNames.themeCandidates("seriph"))
    }

    @Test
    fun `prefixed and scoped theme names pass through`() {
        assertEquals(listOf("@slidev/theme-seriph"), SlidevPackageNames.themeCandidates("@slidev/theme-seriph"))
        assertEquals(listOf("slidev-theme-mine"), SlidevPackageNames.themeCandidates("slidev-theme-mine"))
        assertEquals(listOf("@org/custom-theme"), SlidevPackageNames.themeCandidates("@org/custom-theme"))
    }

    @Test
    fun `theme none and local paths yield no packages`() {
        assertTrue(SlidevPackageNames.themeCandidates("none").isEmpty())
        assertTrue(SlidevPackageNames.themeCandidates("./my-theme").isEmpty())
        assertTrue(SlidevPackageNames.themeCandidates("/abs/theme").isEmpty())
    }

    @Test
    fun `bare addon name probes prefixed then verbatim package`() {
        assertEquals(listOf("slidev-addon-excalidraw", "excalidraw"), SlidevPackageNames.addonCandidates("excalidraw"))
    }

    @Test
    fun `prefixed and scoped addon names pass through`() {
        assertEquals(listOf("slidev-addon-excalidraw"), SlidevPackageNames.addonCandidates("slidev-addon-excalidraw"))
        assertEquals(listOf("@org/my-addon"), SlidevPackageNames.addonCandidates("@org/my-addon"))
    }

    @Test
    fun `blank and local addon names yield no packages`() {
        assertTrue(SlidevPackageNames.addonCandidates("").isEmpty())
        assertTrue(SlidevPackageNames.addonCandidates("./local-addon").isEmpty())
    }

    @Test
    fun `local path detection`() {
        assertTrue(SlidevPackageNames.isLocalPath("./theme"))
        assertTrue(SlidevPackageNames.isLocalPath("../theme"))
        assertTrue(SlidevPackageNames.isLocalPath("/abs/theme"))
        assertFalse(SlidevPackageNames.isLocalPath("seriph"))
        assertFalse(SlidevPackageNames.isLocalPath("@slidev/theme-seriph"))
    }
}
