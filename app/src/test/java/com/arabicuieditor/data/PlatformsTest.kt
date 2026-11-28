package com.arabicuieditor.data

import com.arabicuieditor.data.model.Platforms
import com.arabicuieditor.data.model.newProject
import com.arabicuieditor.data.storage.ProjectJsonCodec
import com.arabicuieditor.ui.editor.PreviewSizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformsTest {
    @Test fun androidProjectIsDefaultAndKeepsPhoneSize() {
        val p = newProject("م", "الرئيسية")
        assertEquals(Platforms.ANDROID, p.platform)
        assertEquals("", p.uiTech)
        assertEquals(360f, p.screens.first().width)
        assertFalse(ProjectJsonCodec.encode(p).contains("\"platform\""))
    }

    @Test fun windowsProjectStartsWithWindowSizeAndRoundTrips() {
        val p = newProject("م", "الرئيسية", platform = Platforms.WINDOWS, uiTech = Platforms.TECH_WPF)
        assertEquals(1280f, p.screens.first().width)
        assertEquals(720f, p.screens.first().height)
        val back = ProjectJsonCodec.decode(ProjectJsonCodec.encode(p))
        assertEquals(Platforms.WINDOWS, back.platform)
        assertEquals(Platforms.TECH_WPF, back.uiTech)
    }

    @Test fun windowsDefaultsToWinUiAndBadValuesAreNormalized() {
        assertEquals(Platforms.TECH_WINUI, newProject("م", "ش", platform = Platforms.WINDOWS).uiTech)
        assertEquals(Platforms.ANDROID, Platforms.normalize("linux"))
        assertEquals("", Platforms.normalizeTech("c#"))
        assertEquals("", newProject("م", "ش", platform = Platforms.ANDROID, uiTech = Platforms.TECH_HTML).uiTech)
    }

    @Test fun oldJsonWithoutPlatformLoadsAsAndroid() {
        val old = ProjectJsonCodec.encode(newProject("م", "ش"))
        assertEquals(Platforms.ANDROID, ProjectJsonCodec.decode(old).platform)
    }

    @Test fun previewSizesAreSeparatedPerPlatform() {
        assertTrue(PreviewSizes.forPlatform(Platforms.WINDOWS).all { it.width >= 800f })
        assertEquals(PreviewSizes.presets, PreviewSizes.forPlatform(Platforms.ANDROID))
        val (w, h) = PreviewSizes.next(1280f, 720f, 1280f, 720f, PreviewSizes.windowsPresets)
        assertTrue(w != 1280f || h != 720f)
    }
}
