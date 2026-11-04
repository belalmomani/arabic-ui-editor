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

    @Test
    fun shiftKeepsGroupInsideBounds() {
        val bounds = DesignRect(0f, 0f, 200f, 100f)
        val moved = SmartAlign.shiftGroup(frames, 500f, 0f, bounds)
        // الصندوق المحيط يمتد حتى x=160 فأقصى إزاحة 40
        assertEquals(50f, moved.getValue("a").x, 0.001f)
        assertEquals(140f, moved.getValue("b").x, 0.001f)
        assertEquals(20f, moved.getValue("a").y, 0.001f)
        assertTrue(SmartAlign.shiftGroup(frames, 0f, 0f, bounds).isEmpty())
        val up = SmartAlign.shiftGroup(frames, 0f, -500f, bounds)
        assertEquals(0f, up.getValue("a").y, 0.001f)
        assertEquals(30f, up.getValue("b").y, 0.001f)
    }
}
