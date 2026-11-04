package com.arabicuieditor.data

import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.LibraryLink
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryLinkTest {
    private fun el(id: String, x: Float, y: Float, text: String, kids: List<UiElement> = emptyList()) = UiElement(
        id = id, type = ElementTypes.CONTAINER,
        layout = ElementLayout(x, y, SizeSpec.fixed(100f), SizeSpec.fixed(50f)),
        properties = mapOf("text" to text), children = kids
    )

    @Test
    fun tagAndUntag() {
        val e = LibraryLink.tag(el("a", 0f, 0f, "t"), "comp1")
        assertEquals("comp1", LibraryLink.linkedId(e))
        assertNull(LibraryLink.linkedId(LibraryLink.untag(e)))
    }

    @Test
    fun refreshReplacesLinkedKeepingIdAndPosition() {
        val source = el("src", 0f, 0f, "جديد", listOf(el("k", 5f, 5f, "ابن")))
        val inst = LibraryLink.tag(el("i1", 30f, 40f, "قديم"), "c")
        val other = el("o", 1f, 1f, "غير مرتبط")
        val nested = el("n", 0f, 0f, "أب", listOf(LibraryLink.tag(el("i2", 7f, 8f, "قديم"), "c")))
        val out = LibraryLink.refresh(listOf(inst, other, nested), "c", source)

        assertEquals("i1", out[0].id)
        assertEquals(30f, out[0].layout.x, 0.001f)
        assertEquals(40f, out[0].layout.y, 0.001f)
        assertEquals("جديد", out[0].properties["text"])
        assertEquals("c", LibraryLink.linkedId(out[0]))
        assertEquals(1, out[0].children.size)
        assertNotEquals("k", out[0].children[0].id)
        assertEquals(other, out[1])
        assertEquals("i2", out[2].children[0].id)
        assertEquals("جديد", out[2].children[0].properties["text"])
        assertEquals(2, LibraryLink.count(out, "c"))
    }

    @Test
    fun reIdKeepsStructureForPaste() {
        val src = el("a", 1f, 2f, "أب", listOf(el("k", 3f, 4f, "ابن")))
        val copy = com.arabicuieditor.data.model.ElementTree.reId(src)
        assertNotEquals(src.id, copy.id)
        assertNotEquals(src.children[0].id, copy.children[0].id)
        assertEquals("ابن", copy.children[0].properties["text"])
    }
}
