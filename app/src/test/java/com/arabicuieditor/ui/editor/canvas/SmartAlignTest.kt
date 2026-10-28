package com.arabicuieditor.ui.editor.canvas

import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.newElement
import com.arabicuieditor.data.storage.ProjectJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** المحاذاة الذكية: الالتقاط، والخطوط الإرشادية، والتباعد المتساوي، والشبكة، وأوامر المحاذاة والتوزيع. */
class SmartAlignTest {

    private val screen = DesignRect(0f, 0f, 360f, 640f)
    private val objects = SnapSettings(objects = true, grid = 0)

    @Test
    fun snapsToScreenEdgeAndShowsAGuide() {
        val r = SmartAlign.snapMove(DesignRect(3f, 100f, 50f, 50f), emptyList(), screen, 6f, objects)
        assertEquals(0f, r.rect.x, 0.001f)
        assertEquals(100f, r.rect.y, 0.001f)
        assertTrue(r.guides.any { it.vertical && it.pos == 0f })
    }

    @Test
    fun snapsCenterToScreenCenter() {
        val r = SmartAlign.snapMove(DesignRect(157f, 300f, 50f, 50f), emptyList(), screen, 6f, objects)
        assertEquals(155f, r.rect.x, 0.001f)
    }

    @Test
    fun snapsEdgeToASiblingEdge() {
        val sibling = DesignRect(100f, 0f, 50f, 50f)
        val r = SmartAlign.snapMove(DesignRect(46f, 200f, 50f, 50f), listOf(sibling), screen, 6f, objects)
        // الحافة اليمنى (96) تلتقط الحافة اليسرى للجار (100).
        assertEquals(50f, r.rect.x, 0.001f)
        assertTrue(r.guides.any { it.vertical && it.pos == 100f })
    }

    @Test
    fun doesNothingBeyondTheThreshold() {
        val r = SmartAlign.snapMove(DesignRect(40f, 200f, 50f, 50f), emptyList(), screen, 6f, objects)
        assertEquals(DesignRect(40f, 200f, 50f, 50f), r.rect)
        assertTrue(r.guides.isEmpty())
    }

    @Test
    fun equalSpacingBetweenTwoNeighbours() {
        val a = DesignRect(0f, 100f, 50f, 50f)
        val b = DesignRect(250f, 100f, 50f, 50f)
        // الفجوة الحرة 150 بين الجارين، فالموضع المتساوي x = 50 + 75 = 125.
        val r = SmartAlign.snapMove(DesignRect(127f, 100f, 50f, 50f), listOf(a, b), screen, 6f, objects)
        assertEquals(125f, r.rect.x, 0.001f)
        assertTrue(r.guides.any { it.spacing })
    }

    @Test
    fun gridSnapsTopLeftWhenObjectsAreOff() {
        val r = SmartAlign.snapMove(
            DesignRect(13f, 21f, 50f, 50f), emptyList(), screen, 6f, SnapSettings(objects = false, grid = 8)
        )
        assertEquals(16f, r.rect.x, 0.001f)
        assertEquals(24f, r.rect.y, 0.001f)
        assertTrue(r.guides.isEmpty())
    }

    @Test
    fun noSnapAtAllWhenEverythingIsOff() {
        val start = DesignRect(3f, 3f, 50f, 50f)
        val r = SmartAlign.snapMove(start, emptyList(), screen, 6f, SnapSettings(objects = false, grid = 0))
        assertEquals(start, r.rect)
    }

    @Test
    fun resizeSnapsOnlyTheMovingEdge() {
        val target = DesignRect(200f, 300f, 40f, 40f)
        val start = DesignRect(10f, 10f, 187f, 50f) // الحافة اليمنى 197
        val r = SmartAlign.snapResize(start, HandleType.RIGHT, listOf(target), screen, 6f, objects)
        assertEquals(10f, r.rect.x, 0.001f)
        assertEquals(190f, r.rect.w, 0.001f)
        assertEquals(50f, r.rect.h, 0.001f)
    }

    @Test
    fun resizeNeverCollapsesBelowTheMinimumSize() {
        val start = DesignRect(10f, 10f, 20f, 50f)
        val r = SmartAlign.snapResize(
            start, HandleType.RIGHT, listOf(DesignRect(11f, 300f, 5f, 5f)), screen, 6f, objects
        )
        assertTrue(r.rect.w >= CanvasMath.MIN_ELEMENT_SIZE)
    }

    @Test
    fun alignInsideAContainer() {
        val c = DesignRect(20f, 40f, 200f, 100f)
        val f = DesignRect(60f, 70f, 40f, 20f)
        assertEquals(20f, SmartAlign.alignIn(f, c, AlignEdge.LEFT).x, 0.001f)
        assertEquals(100f, SmartAlign.alignIn(f, c, AlignEdge.CENTER_H).x, 0.001f)
        assertEquals(180f, SmartAlign.alignIn(f, c, AlignEdge.RIGHT).x, 0.001f)
        assertEquals(40f, SmartAlign.alignIn(f, c, AlignEdge.TOP).y, 0.001f)
        assertEquals(80f, SmartAlign.alignIn(f, c, AlignEdge.CENTER_V).y, 0.001f)
        assertEquals(120f, SmartAlign.alignIn(f, c, AlignEdge.BOTTOM).y, 0.001f)
    }

    @Test
    fun distributeKeepsEndsAndEqualizesGaps() {
        val frames = listOf(
            "c" to DesignRect(200f, 0f, 20f, 20f),
            "a" to DesignRect(0f, 0f, 20f, 20f),
            "b" to DesignRect(30f, 0f, 20f, 20f)
        )
        val moved = SmartAlign.distribute(frames, horizontal = true)
        assertEquals(setOf("b"), moved.keys)
        assertEquals(100f, moved.getValue("b").x, 0.001f)
        assertTrue(SmartAlign.distribute(frames.take(2), true).isEmpty())
    }

    @Test
    fun targetsExcludeTheElementAndItsDescendants() {
        val child = newElement(ElementTypes.TEXT, 5f, 5f, "ن")
        val box = newElement(ElementTypes.CONTAINER, 10f, 10f, "").copy(children = listOf(child))
        val other = newElement(ElementTypes.TEXT, 200f, 300f, "آخر")
        val layout = LayoutEngine.layout(UiScreen("s", "ش", elements = listOf(box, other)))
        val targets = SmartAlign.targetsFor(layout, box.id)
        assertEquals(listOf(layout.frames.getValue(other.id)), targets)
        assertFalse(targets.contains(layout.frames.getValue(child.id)))
    }

    @Test
    fun snapSettingsSurviveSaveAndReopen() {
        val p = Project(
            id = "abcdefgh-5", name = "ن", createdAt = 1, updatedAt = 2,
            screens = listOf(UiScreen("s1", "ش")), snapObjects = false, gridSize = 8
        )
        val back = ProjectJsonCodec.decode(ProjectJsonCodec.encode(p))
        assertFalse(back.snapObjects)
        assertEquals(8, back.gridSize)
        val bad = ProjectJsonCodec.decode(
            """{"schemaVersion":4,"id":"abcdefgh-6","name":"ن","createdAt":1,"updatedAt":2,"screens":[],"resources":[],"gridSize":5}"""
        )
        assertEquals(0, bad.gridSize)
        assertTrue(bad.snapObjects)
    }
}
