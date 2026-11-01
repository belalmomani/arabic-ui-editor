package com.arabicuieditor.data

import com.arabicuieditor.data.model.ComponentLibrary
import com.arabicuieditor.data.model.Templates
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.ui.editor.canvas.DesignRect
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import com.arabicuieditor.ui.editor.canvas.ThumbnailMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailMathTest {
    private val text: (Int) -> String = { "ن$it" }

    @Test fun scaleFitsAndIsCapped() {
        assertEquals(0.5f, ThumbnailMath.scaleFor(DesignRect(0f, 0f, 200f, 100f), 100f, 100f), 1e-4f)
        assertEquals(ThumbnailMath.MAX_SCALE, ThumbnailMath.scaleFor(DesignRect(0f, 0f, 10f, 10f), 500f, 500f), 1e-4f)
        assertEquals(1f, ThumbnailMath.scaleFor(DesignRect(0f, 0f, 0f, 10f), 100f, 100f), 1e-4f)
    }

    @Test fun boundsUnionAndEmpty() {
        val sc = UiScreen("s", "s", elements = ComponentLibrary.presets.first { it.id == "pro_login_form" }.build(text))
        val layout = LayoutEngine.layout(sc)
        val b = ThumbnailMath.bounds(layout, layout.paintOrder)
        assertNotNull(b)
        assertTrue(b!!.w > 0f && b.h > 0f)
        layout.frames.values.forEach {
            assertTrue(it.x >= b.x - 0.01f && it.y >= b.y - 0.01f)
            assertTrue(it.x + it.w <= b.x + b.w + 0.01f && it.y + it.h <= b.y + b.h + 0.01f)
        }
        assertNull(ThumbnailMath.bounds(layout, emptyList()))
        assertNull(ThumbnailMath.bounds(layout, listOf("ghost")))
    }

    @Test fun everyPresetHasDrawableBounds() {
        for (p in ComponentLibrary.presets) {
            val sc = UiScreen("t", "", width = 300f, height = 640f, elements = p.build(text))
            val layout = LayoutEngine.layout(sc)
            val b = ThumbnailMath.bounds(layout, layout.paintOrder)
            assertNotNull(p.id, b)
        }
    }

    @Test fun templateFirstScreensLayOutInsideScreen() {
        for (t in Templates.all) {
            val sc = Templates.build(t, emptyList(), text).first()
            val layout = LayoutEngine.layout(sc)
            assertTrue(t.id, layout.frames.isNotEmpty())
        }
    }
}
