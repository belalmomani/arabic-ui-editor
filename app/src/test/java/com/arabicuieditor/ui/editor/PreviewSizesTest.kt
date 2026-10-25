package com.arabicuieditor.ui.editor

import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.UiScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSizesTest {

    @Test
    fun cycleStartsAtDesignSizeAndWrapsAround() {
        var cur = 360f to 640f
        val seen = mutableListOf(cur)
        repeat(PreviewSizes.presets.size) {
            cur = PreviewSizes.next(360f, 640f, cur.first, cur.second)
            seen += cur
        }
        // الدورة: التصميم (360×640 المطابق لقياسي) ثم بقية الأحجام دون تكرار، ثم العودة للتصميم
        assertEquals(360f to 640f, seen.last())
        assertEquals(seen.size - 1, seen.dropLast(1).toSet().size)
        assertTrue(320f to 568f in seen)
        assertTrue(960f to 600f in seen)
    }

    @Test
    fun unknownCurrentSizeGoesBackToDesign() {
        assertEquals(411f to 891f, PreviewSizes.next(411f, 891f, 123f, 456f))
    }

    @Test
    fun customDesignSizeIsInTheCycleToo() {
        // حجم التصميم المخصص أولًا، ثم الأحجام الجاهزة بترتيبها
        assertEquals(320f to 568f, PreviewSizes.next(411f, 891f, 411f, 891f))
    }

    @Test
    fun projectPreviewFallsBackToScreenSize() {
        val screen = UiScreen("s", "ش", 411f, 891f)
        val none = Project("p", "n", createdAt = 0, updatedAt = 0, screens = listOf(screen))
        assertEquals(411f to 891f, none.previewSizeFor(screen))
        val set = none.copy(previewWidth = 600f, previewHeight = 960f)
        assertEquals(600f to 960f, set.previewSizeFor(screen))
        // قيمة واحدة صفر تعني «غير مضبوط»
        assertEquals(411f to 891f, none.copy(previewWidth = 600f).previewSizeFor(screen))
    }
}
