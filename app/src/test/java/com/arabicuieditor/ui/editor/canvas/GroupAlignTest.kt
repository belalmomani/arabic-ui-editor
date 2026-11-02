package com.arabicuieditor.ui.editor.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupAlignTest {
    private val frames = listOf(
        "a" to DesignRect(10f, 20f, 40f, 40f),
        "b" to DesignRect(100f, 50f, 60f, 20f)
    )

    @Test
    fun alignLeftUsesGroupBounds() {
        val out = SmartAlign.alignGroup(frames, AlignEdge.LEFT)
        assertEquals(10f, out.getValue("b").x, 0.001f)
        assertTrue("a" !in out)
    }

    @Test
    fun alignRightAndBottom() {
        val r = SmartAlign.alignGroup(frames, AlignEdge.RIGHT)
        assertEquals(160f - 40f, r.getValue("a").x, 0.001f)
        val b = SmartAlign.alignGroup(frames, AlignEdge.BOTTOM)
        assertEquals(70f - 40f, b.getValue("a").y, 0.001f)
    }

    @Test
    fun centerHorizontally() {
        val out = SmartAlign.alignGroup(frames, AlignEdge.CENTER_H)
        // الصندوق من 10 إلى 160 (عرض 150): مركزه 85
        assertEquals(85f - 20f, out.getValue("a").x, 0.001f)
        assertEquals(85f - 30f, out.getValue("b").x, 0.001f)
    }

    @Test
    fun singleElementDoesNothing() {
        assertTrue(SmartAlign.alignGroup(frames.take(1), AlignEdge.LEFT).isEmpty())
    }
}
