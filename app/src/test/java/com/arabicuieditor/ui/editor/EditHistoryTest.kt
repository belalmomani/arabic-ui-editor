package com.arabicuieditor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

    private var clock = 0L
    private fun history(limit: Int = 100) = EditHistory<Int>(limit = limit, coalesceWindowMs = 700L, now = { clock })

    @Test
    fun undoAndRedoWalkThroughStates() {
        val h = history()
        // الحالة 1 → 2 → 3
        h.record(before = 1)
        h.record(before = 2)
        assertTrue(h.canUndo)
        assertEquals(2, h.undo(current = 3))
        assertEquals(1, h.undo(current = 2))
        assertNull(h.undo(current = 1))
        assertEquals(2, h.redo(current = 1))
        assertEquals(3, h.redo(current = 2))
        assertNull(h.redo(current = 3))
    }

    @Test
    fun newEditClearsRedo() {
        val h = history()
        h.record(1)
        assertEquals(1, h.undo(2))
        assertTrue(h.canRedo)
        h.record(1)
        assertFalse(h.canRedo)
    }

    @Test
    fun sameKeyWithinWindowMergesIntoOneStep() {
        val h = history()
        // سحب: عشرات التعديلات المتتالية بنفس المفتاح = خطوة واحدة تعود إلى الحالة الأولى
        for (state in 10..20) {
            clock += 16
            h.record(before = state, key = "rect:a")
        }
        assertEquals(10, h.undo(current = 21))
        assertFalse(h.canUndo)
    }

    @Test
    fun differentKeyOrLongPauseStartsNewStep() {
        val h = history()
        h.record(1, "rect:a")
        clock += 10
        h.record(2, "prop:a:fontSize")
        clock += 5000
        h.record(3, "prop:a:fontSize")
        assertEquals(3, h.undo(4))
        assertEquals(2, h.undo(3))
        assertEquals(1, h.undo(2))
    }

    @Test
    fun editsWithoutKeyNeverMerge() {
        val h = history()
        h.record(1)
        h.record(2)
        h.record(3)
        assertEquals(3, h.undo(4))
        assertEquals(2, h.undo(3))
        assertEquals(1, h.undo(2))
    }

    @Test
    fun undoBreaksMergeChain() {
        val h = history()
        h.record(1, "k")
        clock += 10
        assertEquals(1, h.undo(2))
        clock += 10
        h.record(1, "k") // بعد تراجع لا يندمج مع ما قبله
        assertEquals(1, h.undo(2))
    }

    @Test
    fun limitDropsOldestSteps() {
        val h = history(limit = 3)
        (1..10).forEach { h.record(it) }
        val seen = mutableListOf<Int>()
        var current = 11
        while (true) {
            val prev = h.undo(current) ?: break
            seen += prev
            current = prev
        }
        assertEquals(listOf(10, 9, 8), seen)
    }
}
