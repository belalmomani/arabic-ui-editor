package com.arabicuieditor.data

import com.arabicuieditor.data.model.Anchor
import com.arabicuieditor.data.model.AnchorEdge
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.Constraints
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.newProject
import com.arabicuieditor.data.storage.ProjectJsonCodec
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstraintsTest {
    private fun el(id: String, x: Float = 0f, y: Float = 0f, w: Float = 100f, h: Float = 40f, children: List<UiElement> = emptyList()) =
        UiElement(
            id = id, type = ElementTypes.BUTTON,
            layout = ElementLayout(x, y, SizeSpec.fixed(w), SizeSpec.fixed(h)),
            children = children
        )

    private fun anchored(e: UiElement, vararg a: Pair<AnchorEdge, Anchor>): UiElement =
        a.fold(e) { acc, (edge, anchor) -> Constraints.write(acc, edge, anchor) }

    private fun screen(vararg els: UiElement) =
        UiScreen("s", "s", width = 360f, height = 640f, elements = els.toList(), childLayout = ChildLayout.CONSTRAINT)

    private fun frame(s: UiScreen, id: String, w: Float = s.width, h: Float = s.height) =
        LayoutEngine.layout(s, w, h).frames.getValue(id)

    private val P = Constraints.PARENT

    @Test fun readWriteRoundTripAndClear() {
        val e = Constraints.write(el("a"), AnchorEdge.LEFT, Anchor("b-1", 12))
        assertEquals(Anchor("b-1", 12), Constraints.read(e, AnchorEdge.LEFT))
        assertTrue(Constraints.hasAny(e))
        val cleared = Constraints.write(e, AnchorEdge.LEFT, null)
        assertNull(Constraints.read(cleared, AnchorEdge.LEFT))
        assertFalse(Constraints.hasAny(cleared))
    }

    @Test fun badStoredValueIsIgnored() {
        val e = el("a").copy(properties = mapOf("anchorL" to "garbage", "anchorR" to "x:notnum"))
        assertNull(Constraints.read(e, AnchorEdge.LEFT))
        assertNull(Constraints.read(e, AnchorEdge.RIGHT))
    }

    @Test fun solveAxisCases() {
        val min = 16f
        assertEquals(10f to 50f, Constraints.solveAxis(10f, null, null, 50f, 99f, min))
        assertEquals(40f to 50f, Constraints.solveAxis(null, 90f, null, 50f, 99f, min))
        assertEquals(10f to 80f, Constraints.solveAxis(10f, 90f, null, 50f, 99f, min))
        assertEquals(75f to 50f, Constraints.solveAxis(null, null, 100f, 50f, 99f, min))
        assertEquals(99f to 50f, Constraints.solveAxis(null, null, null, 50f, 99f, min))
        assertEquals(10f to 16f, Constraints.solveAxis(10f, 12f, null, 50f, 99f, min))
    }

    @Test fun parentMarginsPlaceElement() {
        val s = screen(anchored(el("a", w = 100f, h = 40f), AnchorEdge.LEFT to Anchor(P, 16), AnchorEdge.TOP to Anchor(P, 20)))
        val f = frame(s, "a")
        assertEquals(16f, f.x, 0.01f); assertEquals(20f, f.y, 0.01f)
        assertEquals(100f, f.w, 0.01f)
    }

    @Test fun bothSidesStretchAndAdaptToScreenSize() {
        val s = screen(anchored(el("a", h = 40f), AnchorEdge.LEFT to Anchor(P, 16), AnchorEdge.RIGHT to Anchor(P, 16), AnchorEdge.TOP to Anchor(P, 0)))
        assertEquals(328f, frame(s, "a").w, 0.01f)
        val wide = frame(s, "a", w = 600f, h = 640f)
        assertEquals(568f, wide.w, 0.01f)
        assertEquals(16f, wide.x, 0.01f)
    }

    @Test fun rightAndBottomAnchorsToParent() {
        val s = screen(anchored(el("a", w = 100f, h = 40f), AnchorEdge.RIGHT to Anchor(P, 10), AnchorEdge.BOTTOM to Anchor(P, 10)))
        val f = frame(s, "a")
        assertEquals(250f, f.x, 0.01f); assertEquals(590f, f.y, 0.01f)
        val big = frame(s, "a", w = 500f, h = 800f)
        assertEquals(390f, big.x, 0.01f); assertEquals(750f, big.y, 0.01f)
    }

    @Test fun centeringInParent() {
        val s = screen(anchored(el("a", w = 100f, h = 40f), AnchorEdge.CENTER_H to Anchor(P, 0), AnchorEdge.CENTER_V to Anchor(P, 0)))
        val f = frame(s, "a")
        assertEquals(130f, f.x, 0.01f); assertEquals(300f, f.y, 0.01f)
    }

    @Test fun siblingChainFollowsMovedTarget() {
        val a = anchored(el("a", w = 100f, h = 40f), AnchorEdge.LEFT to Anchor(P, 10), AnchorEdge.TOP to Anchor(P, 10))
        val b = anchored(el("b", w = 80f, h = 40f), AnchorEdge.LEFT to Anchor("a", 8), AnchorEdge.TOP to Anchor("a", 12))
        val s = screen(b, a) // الترتيب معكوس عمدًا: الحل لا يعتمد على ترتيب الإخوة
        val fb = frame(s, "b")
        assertEquals(118f, fb.x, 0.01f)
        assertEquals(62f, fb.y, 0.01f)
    }

    @Test fun rightAnchorToSiblingEndsBeforeIt() {
        val a = anchored(el("a", w = 100f), AnchorEdge.LEFT to Anchor(P, 200f.toInt()))
        val b = anchored(el("b", w = 60f), AnchorEdge.RIGHT to Anchor("a", 10))
        val f = frame(screen(a, b), "b")
        assertEquals(130f, f.x, 0.01f) // 200 - 10 - 60
    }

    @Test fun cycleAndMissingTargetsDoNotCrash() {
        val a = anchored(el("a", x = 5f, y = 5f), AnchorEdge.LEFT to Anchor("b", 0))
        val b = anchored(el("b", x = 7f, y = 7f), AnchorEdge.LEFT to Anchor("a", 0))
        val c = anchored(el("c", x = 9f, y = 9f), AnchorEdge.LEFT to Anchor("ghost", 0), AnchorEdge.TOP to Anchor("c", 0))
        val r = LayoutEngine.layout(screen(a, b, c))
        assertEquals(3, r.frames.size)
        assertEquals(9f, r.frames.getValue("c").x, 0.01f)
        assertEquals(9f, r.frames.getValue("c").y, 0.01f)
    }

    @Test fun unanchoredElementKeepsFreeCoordsAndIsDraggable() {
        val free = el("free", x = 33f, y = 44f)
        val fixed = anchored(el("fx"), AnchorEdge.LEFT to Anchor(P, 0))
        val r = LayoutEngine.layout(screen(free, fixed))
        assertEquals(33f, r.frames.getValue("free").x, 0.01f)
        assertEquals(44f, r.frames.getValue("free").y, 0.01f)
        assertTrue("free" in r.freeIds)
        assertFalse("fx" in r.freeIds)
    }

    @Test fun reIdKeepsInternalAnchorsPointingInsideCopy() {
        val a = el("a")
        val b = anchored(el("b"), AnchorEdge.LEFT to Anchor("a", 5), AnchorEdge.TOP to Anchor("outside", 3))
        val root = el("root", children = listOf(a, b)).copy(childLayout = ChildLayout.CONSTRAINT)
        val copy = ElementTree.reId(root)
        val newA = copy.children[0]
        val newB = copy.children[1]
        assertEquals(Anchor(newA.id, 5), Constraints.read(newB, AnchorEdge.LEFT))
        assertEquals(Anchor("outside", 3), Constraints.read(newB, AnchorEdge.TOP))
        assertFalse(newA.id == "a")
    }

    @Test fun survivesJsonRoundTrip() {
        val b = anchored(el("b"), AnchorEdge.RIGHT to Anchor(P, 24))
        val p = newProject("p", "a").let { it.copy(screens = listOf(screen(b))) }
        val back = ProjectJsonCodec.decode(ProjectJsonCodec.encode(p))
        assertEquals(ChildLayout.CONSTRAINT, back.screens[0].childLayout)
        assertEquals(Anchor(P, 24), Constraints.read(back.screens[0].elements[0], AnchorEdge.RIGHT))
    }

    @Test fun matchParentWithoutAnchorsStillFills() {
        val e = el("a", x = 10f).copy(layout = ElementLayout(10f, 0f, SizeSpec(SizeMode.MATCH_PARENT, 0f), SizeSpec.fixed(40f)))
        assertEquals(350f, frame(screen(e), "a").w, 0.01f)
    }
}
