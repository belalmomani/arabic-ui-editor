package com.arabicuieditor.data

import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.ScreenOps
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.newElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** إدارة الشاشات: تسمية فريدة، نسخ بمعرّفات جديدة، حذف مع إعادة تعيين شاشة البداية. */
class ScreenOpsTest {

    private fun screens() = listOf(
        UiScreen("s1", "الرئيسية", elements = listOf(newElement(ElementTypes.TEXT, 0f, 0f, "س"))),
        UiScreen("s2", "الدخول"),
        UiScreen("s3", "الإعدادات")
    )

    @Test
    fun uniqueNameAppendsNumberAndTrims() {
        assertEquals("الرئيسية 2", ScreenOps.uniqueName(screens(), "الرئيسية"))
        assertEquals("جديدة", ScreenOps.uniqueName(screens(), "  جديدة  "))
        assertEquals("الرئيسية", ScreenOps.uniqueName(screens(), "الرئيسية", excludeId = "s1"))
        assertEquals(60, ScreenOps.uniqueName(emptyList(), "x".repeat(100)).length)
    }

    @Test
    fun renameKeepsNamesUnique() {
        val list = screens()
        val renamed = ScreenOps.rename(list, "s2", "الإعدادات")
        assertEquals("الإعدادات 2", renamed.first { it.id == "s2" }.name)
        assertEquals(list, ScreenOps.rename(list, "missing", "x"))
    }

    @Test
    fun duplicateCopiesElementsWithNewIdsAfterTheOriginal() {
        val (list, copyId) = ScreenOps.duplicate(screens(), "s1", "نسخة")!!
        assertEquals(listOf("s1", copyId, "s2", "s3"), list.map { it.id })
        val copy = list[1]
        assertEquals("الرئيسية نسخة", copy.name)
        assertEquals(1, copy.elements.size)
        assertNotEquals(screens()[0].elements[0].id, copy.elements[0].id)
        assertEquals(screens()[0].elements[0].properties, copy.elements[0].properties)
        assertNull(ScreenOps.duplicate(screens(), "missing", "نسخة"))
    }

    @Test
    fun deleteReassignsStartAndRefusesTheLastScreen() {
        val r = ScreenOps.delete(screens(), "s1", startScreenId = "s1")!!
        assertEquals(listOf("s2", "s3"), r.screens.map { it.id })
        assertEquals("s2", r.startScreenId)
        assertEquals("s2", r.showScreenId)

        val keep = ScreenOps.delete(screens(), "s3", startScreenId = "s2")!!
        assertEquals("s2", keep.startScreenId)
        assertEquals("s2", keep.showScreenId)

        // البداية غير مخزّنة (null) = أول شاشة
        assertEquals("s2", ScreenOps.delete(screens(), "s1", null)!!.startScreenId)
        assertNull(ScreenOps.delete(listOf(UiScreen("only", "وحيدة")), "only", null))
        assertNull(ScreenOps.delete(screens(), "missing", null))
        assertNotNull(ScreenOps.delete(screens(), "s2", "s1"))
        assertEquals(0, ElementTree.count(ScreenOps.delete(screens(), "s1", null)!!.screens.flatMap { it.elements }))
    }
}
