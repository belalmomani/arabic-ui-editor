package com.arabicuieditor.data

import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementOps
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.EventBinding
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.withProp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementOpsTest {

    private fun el(id: String, x: Float = 0f, y: Float = 0f, w: Float = 100f, h: Float = 40f, name: String? = null) =
        UiElement(
            id = id,
            type = ElementTypes.BUTTON,
            layout = ElementLayout(x, y, SizeSpec.fixed(w), SizeSpec.fixed(h)),
            properties = if (name == null) emptyMap() else mapOf(PropertyKeys.NAME to name)
        )

    private fun ids(list: List<UiElement>) = list.map { it.id }

    private val abc = listOf(el("a"), el("b"), el("c"))

    @Test
    fun forwardAndBackwardSwapNeighbours() {
        assertEquals(listOf("b", "a", "c"), ids(ElementOps.moveForward(abc, "a")))
        assertEquals(listOf("a", "c", "b"), ids(ElementOps.moveBackward(abc, "c")))
    }

    @Test
    fun reorderAtEdgesIsNoOp() {
        assertEquals(abc, ElementOps.moveForward(abc, "c"))
        assertEquals(abc, ElementOps.moveBackward(abc, "a"))
        assertEquals(abc, ElementOps.bringToFront(abc, "c"))
        assertEquals(abc, ElementOps.sendToBack(abc, "a"))
        assertEquals(abc, ElementOps.moveForward(abc, "missing"))
    }

    @Test
    fun frontAndBackJumpToEnds() {
        assertEquals(listOf("b", "c", "a"), ids(ElementOps.bringToFront(abc, "a")))
        assertEquals(listOf("c", "a", "b"), ids(ElementOps.sendToBack(abc, "c")))
    }

    @Test
    fun duplicatePlacesCopyAboveOriginalWithNewIdAndOffset() {
        val child = el("child").copy(events = listOf(EventBinding("ev1", "click", "noop")))
        val parent = el("p", x = 10f, y = 20f, name = "زر الدخول").copy(children = listOf(child))
        val (list, copyId) = ElementOps.duplicate(listOf(parent, el("z")), "p", 12f, "نسخة")!!
        assertEquals(3, list.size)
        assertEquals("p", list[0].id)
        assertEquals(copyId, list[1].id)
        val copy = list[1]
        assertNotEquals("p", copy.id)
        assertEquals(22f, copy.layout.x, 0f)
        assertEquals(32f, copy.layout.y, 0f)
        assertEquals("زر الدخول نسخة", copy.properties[PropertyKeys.NAME])
        // الأبناء والأحداث بمعرّفات جديدة أيضًا
        assertNotEquals("child", copy.children.single().id)
        assertNotEquals("ev1", copy.children.single().events.single().id)
        // الأصل لم يتغير
        assertEquals(parent, list[0])
    }

    @Test
    fun duplicatingTwiceGivesUniqueNames() {
        val start = listOf(el("p", name = "زر"))
        val (once, id1) = ElementOps.duplicate(start, "p", 5f, "نسخة")!!
        val (twice, _) = ElementOps.duplicate(once, "p", 5f, "نسخة")!!
        val names = twice.map { it.properties[PropertyKeys.NAME] }
        assertEquals(names.size, names.toSet().size)
        assertNotNull(id1)
        assertNull(ElementOps.duplicate(start, "missing", 5f, "نسخة"))
    }

    @Test
    fun uniqueNameCountsUp() {
        val existing = listOf(el("a", name = "نص"), el("b", name = "نص 2"))
        assertEquals("نص 3", ElementOps.uniqueName(existing, "نص"))
        assertEquals("زر", ElementOps.uniqueName(existing, "زر"))
    }

    @Test
    fun withUniqueNamesConsidersNewElementsToo() {
        val added = listOf(el("x"), el("y"))
        val named = ElementOps.withUniqueNames(emptyList(), added) { "زر" }
        assertEquals(listOf("زر", "زر 2"), named.map { it.properties[PropertyKeys.NAME] })
        // اسم موجود مسبقًا في العنصر المضاف يُحترم
        val keep = ElementOps.withUniqueNames(emptyList(), listOf(el("x").withProp(PropertyKeys.NAME, "عنوان"))) { "زر" }
        assertEquals("عنوان", keep.single().properties[PropertyKeys.NAME])
    }

    @Test
    fun placeCenteredKeepsGroupShapeAndStaysInsideFrame() {
        val group = listOf(el("card", 0f, 0f, 200f, 120f), el("title", 16f, 14f, 100f, 20f))
        val placed = ElementOps.placeCentered(group, cx = 180f, cy = 320f, frameW = 360f, frameH = 640f)
        // العلاقة النسبية محفوظة
        assertEquals(16f, placed[1].layout.x - placed[0].layout.x, 0f)
        assertEquals(14f, placed[1].layout.y - placed[0].layout.y, 0f)
        // المركز عند النقطة المطلوبة
        assertEquals(80f, placed[0].layout.x, 0f)
        assertEquals(260f, placed[0].layout.y, 0f)
        // عند الحافة يبقى داخل الإطار
        val edge = ElementOps.placeCentered(group, cx = 5000f, cy = 5000f, frameW = 360f, frameH = 640f)
        assertTrue(edge[0].layout.x + 200f <= 360f)
        assertTrue(edge[0].layout.y + 120f <= 640f)
    }
}
