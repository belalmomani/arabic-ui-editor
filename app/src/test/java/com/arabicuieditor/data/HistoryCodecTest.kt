package com.arabicuieditor.data

import com.arabicuieditor.data.storage.HistoryCodec
import com.arabicuieditor.ui.editor.EditHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCodecTest {

    @Test
    fun roundTrip() {
        val text = HistoryCodec.encode(listOf("{\"a\":1}", "x"), listOf("y"))
        val (u, r) = HistoryCodec.decode(text)!!
        assertEquals(listOf("{\"a\":1}", "x"), u)
        assertEquals(listOf("y"), r)
    }

    @Test
    fun garbageIsNull() {
        assertNull(HistoryCodec.decode("ليس json"))
    }

    @Test
    fun historyExportKeepsNewestAndRestores() {
        val h = EditHistory<Int>()
        (1..30).forEach { h.record(it) }
        val (u, r) = h.export(15, 5)
        assertEquals((16..30).toList(), u)
        assertTrue(r.isEmpty())

        val h2 = EditHistory<Int>()
        h2.restore(u, listOf(99))
        assertTrue(h2.canUndo)
        assertTrue(h2.canRedo)
        assertEquals(30, h2.undo(31))
        assertEquals(31, h2.redo(30))
    }
}
