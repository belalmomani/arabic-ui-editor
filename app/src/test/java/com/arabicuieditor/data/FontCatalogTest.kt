package com.arabicuieditor.data

import com.arabicuieditor.data.export.ComposeExporter
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.FontCatalog
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.newProject
import com.arabicuieditor.data.model.prop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontCatalogTest {
    @Test fun candidateIdsAreValidAndUnique() {
        val ids = FontCatalog.candidates.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue(it, FontCatalog.isValid(it)) }
        assertTrue(ids.none { it in FontCatalog.system })
    }

    @Test fun validity() {
        assertTrue(FontCatalog.isValid("cairo"))
        assertTrue(FontCatalog.isValid("noto_kufi_arabic"))
        assertTrue(FontCatalog.isValid("serif"))
        assertFalse(FontCatalog.isValid(""))
        assertFalse(FontCatalog.isValid("Cairo"))
        assertFalse(FontCatalog.isValid("1cairo"))
        assertFalse(FontCatalog.isValid("a-b"))
        assertFalse(FontCatalog.isValid("../x"))
        assertFalse(FontCatalog.isValid("x".repeat(41)))
    }

    @Test fun registryNormalizesFontFamily() {
        assertEquals("cairo", PropertyRegistry.normalize(PropertyKeys.FONT_FAMILY, "  cairo "))
        assertNull(PropertyRegistry.normalize(PropertyKeys.FONT_FAMILY, "Bad Name!"))
        assertEquals("default", PropertyRegistry.defaultOf(ElementTypes.TEXT, PropertyKeys.FONT_FAMILY))
        assertEquals("", PropertyRegistry.defaultOf(ElementTypes.DIVIDER, PropertyKeys.FONT_FAMILY))
    }

    private fun text(font: String?) = UiElement(
        id = "t", type = ElementTypes.TEXT,
        layout = ElementLayout(0f, 0f, SizeSpec.fixed(100f), SizeSpec.fixed(30f)),
        properties = buildMap {
            put(PropertyKeys.TEXT, "مرحبا")
            if (font != null) put(PropertyKeys.FONT_FAMILY, font)
        }
    )

    private fun export(e: UiElement) = ComposeExporter.export(
        newProject("p", "a").let { it.copy(screens = listOf(UiScreen("s", "a", elements = listOf(e))), startScreenId = "s") }
    )

    @Test fun exportDefaultHasNoFontFamily() {
        val src = export(text(null))
        assertFalse(src.contains("fontFamily ="))
        assertFalse(src.contains("import com.example.app.R"))
    }

    @Test fun exportSystemAndCustom() {
        assertTrue(export(text("serif")).contains("fontFamily = FontFamily.Serif"))
        val custom = export(text("cairo"))
        assertTrue(custom.contains("fontFamily = FontFamily(Font(R.font.cairo))"))
        assertTrue(custom.contains("import com.example.app.R"))
    }

    @Test fun elementPropReadsFont() = assertEquals("mono", text("mono").prop(PropertyKeys.FONT_FAMILY))
}
