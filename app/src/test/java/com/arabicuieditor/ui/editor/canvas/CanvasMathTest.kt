package com.arabicuieditor.ui.editor.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasMathTest {

    private val density = 2.5f // هاتف صغير نموذجي

    @Test
    fun zoomKeepsPointUnderFingersFixed() {
        val vp = Viewport(1f, 30f, 50f)
        val cx = 400f
        val cy = 700f
        val before = CanvasMath.toDesignX(vp, density, cx) to CanvasMath.toDesignY(vp, density, cy)
        val zoomed = CanvasMath.transform(vp, 1.8f, 0f, 0f, cx, cy)
        val after = CanvasMath.toDesignX(zoomed, density, cx) to CanvasMath.toDesignY(zoomed, density, cy)
        assertEquals(before.first, after.first, 0.01f)
        assertEquals(before.second, after.second, 0.01f)
    }

    @Test
    fun zoomIsClamped() {
        val vp = Viewport(1f, 0f, 0f)
        assertEquals(CanvasMath.MAX_SCALE, CanvasMath.transform(vp, 100f, 0f, 0f, 0f, 0f).scale, 0f)
        assertEquals(CanvasMath.MIN_SCALE, CanvasMath.transform(vp, 0.001f, 0f, 0f, 0f, 0f).scale, 0f)
    }

    @Test
    fun panMovesOffset() {
        val moved = CanvasMath.transform(Viewport(1f, 10f, 20f), 1f, 15f, -5f, 0f, 0f)
        assertEquals(25f, moved.offsetX, 0.001f)
        assertEquals(15f, moved.offsetY, 0.001f)
    }

    @Test
    fun fitShowsWholeFrameCentered() {
        // شاشة 360x640dp على مساحة 1000x1500px
        val vp = CanvasMath.fit(1000f, 1500f, 360f, 640f, density, 40f)
        val frame = CanvasMath.toScreenRect(vp, density, DesignRect(0f, 0f, 360f, 640f))
        assertTrue(frame.left >= 40f - 0.5f && frame.right <= 960f + 0.5f)
        assertTrue(frame.top >= 40f - 0.5f && frame.bottom <= 1460f + 0.5f)
        assertEquals(500f, (frame.left + frame.right) / 2f, 0.5f)
        assertEquals(750f, (frame.top + frame.bottom) / 2f, 0.5f)
    }

    @Test
    fun screenAndDesignConversionsRoundTrip() {
        val vp = Viewport(1.7f, -33f, 81f)
        val sx = CanvasMath.toScreenX(vp, density, 123f)
        assertEquals(123f, CanvasMath.toDesignX(vp, density, sx), 0.001f)
    }

    @Test
    fun hitTestPrefersTopmostAndHonorsSlop() {
        val rects = listOf(
            "bottom" to DesignRect(0f, 0f, 100f, 100f),
            "top" to DesignRect(50f, 50f, 100f, 100f)
        )
        assertEquals("top", CanvasMath.hitTest(rects, 60f, 60f, 0f))
        assertEquals("bottom", CanvasMath.hitTest(rects, 10f, 10f, 0f))
        assertNull(CanvasMath.hitTest(rects, 200f, 200f, 0f))
        // عنصر رفيع (فاصل) يُلمس بهامش
        val thin = listOf("divider" to DesignRect(0f, 100f, 200f, 12f))
        assertNull(CanvasMath.hitTest(thin, 10f, 95f, 0f))
        assertEquals("divider", CanvasMath.hitTest(thin, 10f, 95f, 6f))
    }

    @Test
    fun resizeFromTopLeftKeepsOppositeCornerFixed() {
        val r = CanvasMath.resize(DesignRect(50f, 50f, 100f, 40f), HandleType.TOP_LEFT, 20f, 10f)
        assertEquals(DesignRect(70f, 60f, 80f, 30f), r)
        assertEquals(150f, r.right, 0f)
        assertEquals(90f, r.bottom, 0f)
    }

    @Test
    fun resizeNeverGoesBelowMinimumAndKeepsAnchor() {
        val r = CanvasMath.resize(DesignRect(50f, 50f, 100f, 40f), HandleType.TOP_LEFT, 500f, 500f)
        assertEquals(CanvasMath.MIN_ELEMENT_SIZE, r.w, 0f)
        assertEquals(CanvasMath.MIN_ELEMENT_SIZE, r.h, 0f)
        assertEquals(150f, r.right, 0f)
        assertEquals(90f, r.bottom, 0f)
        val br = CanvasMath.resize(DesignRect(50f, 50f, 100f, 40f), HandleType.BOTTOM_RIGHT, -500f, -500f)
        assertEquals(DesignRect(50f, 50f, 16f, 16f), br)
    }

    @Test
    fun edgeHandleChangesOnlyOneDimension() {
        val r = CanvasMath.resize(DesignRect(10f, 10f, 100f, 40f), HandleType.RIGHT, 30f, 99f)
        assertEquals(DesignRect(10f, 10f, 130f, 40f), r)
    }

    @Test
    fun moveKeepsPartOfElementInsideFrame() {
        val r = CanvasMath.move(DesignRect(10f, 10f, 100f, 40f), 5000f, 5000f, 360f, 640f)
        assertEquals(360f - CanvasMath.MIN_ELEMENT_SIZE, r.x, 0f)
        assertEquals(640f - CanvasMath.MIN_ELEMENT_SIZE, r.y, 0f)
        val l = CanvasMath.move(DesignRect(10f, 10f, 100f, 40f), -5000f, -5000f, 360f, 640f)
        assertEquals(CanvasMath.MIN_ELEMENT_SIZE - 100f, l.x, 0f)
    }

    @Test
    fun smallElementsHideEdgeHandlesButKeepCorners() {
        val small = ScreenRect(0f, 0f, 100f, 60f)
        assertEquals(4, CanvasMath.handlePositions(small, 240f).size)
        val big = ScreenRect(0f, 0f, 500f, 400f)
        assertEquals(8, CanvasMath.handlePositions(big, 240f).size)
    }

    @Test
    fun handleHitUsesWideRadiusOutsideAndNarrowInside() {
        val rect = ScreenRect(100f, 100f, 300f, 200f)
        val handles = CanvasMath.handlePositions(rect, 1000f)
        // خارج الإطار بـ 40px من الزاوية العلوية اليسرى: ضمن نصف القطر الواسع
        assertEquals(
            HandleType.TOP_LEFT,
            CanvasMath.hitHandle(handles, rect, 70f, 80f, outerRadius = 70f, innerRadius = 30f)
        )
        // داخل العنصر بـ 50px من الزاوية: خارج نصف القطر الضيق، فيبقى للتحريك
        assertNull(CanvasMath.hitHandle(handles, rect, 135f, 135f, outerRadius = 70f, innerRadius = 30f))
        // داخل العنصر وقريب جدًا من الزاوية: مقبض
        assertNotNull(CanvasMath.hitHandle(handles, rect, 110f, 110f, outerRadius = 70f, innerRadius = 30f))
    }
}
