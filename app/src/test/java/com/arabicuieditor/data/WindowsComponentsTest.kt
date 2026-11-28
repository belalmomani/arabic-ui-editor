package com.arabicuieditor.data

import com.arabicuieditor.data.export.WindowsProjectExporter
import com.arabicuieditor.data.model.ComponentLibrary
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.Platforms
import com.arabicuieditor.data.model.PresetCategory
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.newId
import com.arabicuieditor.data.model.newProject
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class WindowsComponentsTest {
    private val presets get() = ComponentLibrary.byCategory(PresetCategory.WINDOWS)

    @Test fun coversTheRequestedKinds() {
        val ids = presets.map { it.id }.toSet()
        for (k in listOf("sidebar", "toolbar", "table", "dialog", "settings", "dashboard", "floating_panel", "login", "file_manager", "report")) {
            assertTrue("win_$k missing", "win_$k" in ids)
        }
        assertTrue(presets.all { it.id.startsWith("win_") })
    }

    @Test fun eachPresetIsOneNestedRootWithPositiveFrames() {
        for (p in presets) {
            val built = p.build { "نص" }
            assertEquals(p.id, 1, built.size)
            assertTrue("${p.id} too small", ElementTree.flatten(built).size > 1)
            val screen = UiScreen(newId(), "ش", 1280f, 720f, elements = built)
            val r = LayoutEngine.layout(screen)
            assertEquals(p.id, ElementTree.count(built), r.frames.size)
            for ((id, f) in r.frames) assertTrue("${p.id}/$id", f.w > 0f && f.h > 0f)
        }
    }

    @Test fun allPresetsExportInEveryTechnology() {
        for (tech in Platforms.WINDOWS_TECHS) {
            val base = newProject("م", "ش", platform = Platforms.WINDOWS, uiTech = tech)
            val elements = presets.map { it.build { s -> "نص" }.single() }
            val project = base.copy(screens = listOf(base.screens.first().copy(elements = elements)))
            val files = WindowsProjectExporter.files(project)
            assertTrue(tech, files.isNotEmpty())
            files.filterKeys { it.endsWith(".xaml") }.forEach { (n, b) ->
                try {
                    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(b))
                } catch (e: Exception) {
                    throw AssertionError("$tech/$n: ${e.message}")
                }
            }
        }
    }
}
