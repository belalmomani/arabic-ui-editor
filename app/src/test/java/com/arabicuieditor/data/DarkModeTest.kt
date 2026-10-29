package com.arabicuieditor.data

import com.arabicuieditor.data.model.Contrast
import com.arabicuieditor.data.model.DarkMode
import com.arabicuieditor.data.model.DesignSystem
import com.arabicuieditor.data.model.DesignThemes
import com.arabicuieditor.data.model.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkModeTest {
    private val light = DesignSystem.DEFAULT

    @Test fun lightBecomesDark() {
        val d = DarkMode.apply(light)
        assertTrue(DarkMode.isDark(d))
        assertTrue(Contrast.luminance(d.color(Tokens.BACKGROUND)) < 0.05)
        assertTrue(Contrast.luminance(d.color(Tokens.TEXT_COLOR)) > 0.6)
    }

    @Test fun originalIsNotChanged() {
        val before = light.colors
        DarkMode.apply(light)
        assertEquals(before, light.colors)
    }

    @Test fun idempotent() {
        val once = DarkMode.apply(light)
        assertEquals(once, DarkMode.apply(once))
    }

    @Test fun alreadyDarkThemeUnchanged() {
        val night = DesignThemes.all.first { it.id == "night" }
        val ds = DesignSystem.DEFAULT.copy(colors = night.colors)
        assertTrue(DarkMode.isDark(ds))
        assertEquals(ds, DarkMode.apply(ds))
    }

    @Test fun textAndAccentsAreReadableOnEveryTheme() {
        for (theme in DesignThemes.all) {
            val d = DarkMode.apply(DesignSystem.DEFAULT.copy(colors = theme.colors))
            val bg = d.color(Tokens.BACKGROUND)
            assertTrue(theme.id, Contrast.ratio(d.color(Tokens.TEXT_COLOR), bg) >= 7.0)
            assertTrue(theme.id, Contrast.ratio(d.color(Tokens.MUTED), bg) >= 4.5)
            assertTrue(theme.id, Contrast.ratio(d.color(Tokens.PRIMARY), bg) >= 3.0)
            assertTrue(theme.id, Contrast.ratio(d.color(Tokens.DANGER), bg) >= 3.0)
        }
    }

    @Test fun hslRoundTrip() {
        val c = 0xFF336699.toInt()
        val hsl = DarkMode.toHsl(c)
        val back = DarkMode.fromHsl(hsl[0], hsl[1], hsl[2], 0xFF)
        for (shift in listOf(16, 8, 0)) {
            val d = ((c shr shift) and 0xFF) - ((back shr shift) and 0xFF)
            assertTrue(kotlin.math.abs(d) <= 1)
        }
    }

    @Test fun lightDefaultIsNotDark() = assertFalse(DarkMode.isDark(light))
}
