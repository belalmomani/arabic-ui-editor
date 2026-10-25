package com.arabicuieditor.ui.editor.canvas

import com.arabicuieditor.data.model.Aligns
import com.arabicuieditor.data.model.Arrangements
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Pins
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** اختبار محرّك التخطيط النقي: صف/عمود/صندوق، الأبعاد المرنة، المحاذاة، الهوامش، التثبيت عند تغيّر الحجم. */
class LayoutEngineTest {

    private fun el(
        id: String,
        w: SizeSpec = SizeSpec.fixed(100f),
        h: SizeSpec = SizeSpec.fixed(40f),
        x: Float = 0f,
        y: Float = 0f,
        props: Map<String, String> = emptyMap()
    ) = UiElement(
        id = id,
        type = ElementTypes.BUTTON,
        layout = ElementLayout(x, y, w, h),
        properties = props
    )

    private fun box(
        id: String,
        layout: ChildLayout,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        props: Map<String, String> = emptyMap(),
        children: List<UiElement> = emptyList()
    ) = UiElement(
        id = id,
        type = ElementTypes.CONTAINER,
        layout = ElementLayout(x, y, SizeSpec.fixed(w), SizeSpec.fixed(h)),
        childLayout = layout,
        properties = props,
        children = children
    )

    private fun screenOf(vararg e: UiElement) = UiScreen("s", "ش", 360f, 640f, e.toList())

    private fun LayoutResult.f(id: String) = frames.getValue(id)

    private val pad10 = mapOf(
        PropertyKeys.PADDING_START to "10", PropertyKeys.PADDING_END to "10",
        PropertyKeys.PADDING_TOP to "10", PropertyKeys.PADDING_BOTTOM to "10"
    )

    // ---- تخطيط حر ----

    @Test
    fun freeLayoutKeepsPositionsAndNestsChildrenInsideParent() {
        val child = el("c", x = 5f, y = 6f)
        val parent = box("p", ChildLayout.ABSOLUTE, 10f, 20f, 200f, 100f, children = listOf(child))
        val r = LayoutEngine.layout(screenOf(parent))
        assertEquals(10f, r.f("p").x, 0.01f)
        // موضع الابن نسبةً إلى أبيه، والإطار بإحداثيات الشاشة
        assertEquals(15f, r.f("c").x, 0.01f)
        assertEquals(26f, r.f("c").y, 0.01f)
        assertEquals(listOf("p", "c"), r.paintOrder)
        assertEquals("p", r.parentOf["c"])
        assertNull(r.parentOf["p"])
        assertTrue("c" in r.freeIds && "p" in r.freeIds)
        assertTrue("p" in r.containers)
    }

    @Test
    fun matchParentInFreeLayoutFillsFromItsPositionToTheEdge() {
        val a = el("a", w = SizeSpec(SizeMode.MATCH_PARENT, 100f), x = 20f)
        val r = LayoutEngine.layout(screenOf(a))
        assertEquals(340f, r.f("a").w, 0.01f)
        // وعند معاينة أعرض يتمدد معها
        val wide = LayoutEngine.layout(screenOf(a), 480f, 640f)
        assertEquals(460f, wide.f("a").w, 0.01f)
    }

    // ---- العمود ----

    @Test
    fun columnStacksChildrenWithPaddingAndSpacing() {
        val c = box(
            "c", ChildLayout.COLUMN, 10f, 20f, 200f, 300f,
            props = pad10 + (PropertyKeys.SPACING to "8"),
            children = listOf(
                el("a", SizeSpec.fixed(100f), SizeSpec.fixed(40f)),
                el("b", SizeSpec.fixed(100f), SizeSpec.fixed(60f))
            )
        )
        val r = LayoutEngine.layout(screenOf(c))
        assertEquals(20f, r.f("a").x, 0.01f)
        assertEquals(30f, r.f("a").y, 0.01f)
        assertEquals(20f, r.f("b").x, 0.01f)
        assertEquals(78f, r.f("b").y, 0.01f)
        assertFalse("a" in r.freeIds)
        assertEquals(ChildLayout.COLUMN, r.parentLayoutOf("a"))
    }

    @Test
    fun columnCrossAlignmentCentersAndEnds() {
        fun xOf(align: String): Float {
            val c = box(
                "c", ChildLayout.COLUMN, 0f, 0f, 200f, 300f,
                props = mapOf(PropertyKeys.CHILD_ALIGN_H to align),
                children = listOf(el("a"))
            )
            return LayoutEngine.layout(screenOf(c)).f("a").x
        }
        assertEquals(0f, xOf(Aligns.START), 0.01f)
        assertEquals(50f, xOf(Aligns.CENTER), 0.01f)
        assertEquals(100f, xOf(Aligns.END), 0.01f)
    }

    @Test
    fun selfAlignmentOverridesContainerAlignment() {
        val c = box(
            "c", ChildLayout.COLUMN, 0f, 0f, 200f, 300f,
            props = mapOf(PropertyKeys.CHILD_ALIGN_H to Aligns.START),
            children = listOf(el("a", props = mapOf(PropertyKeys.SELF_ALIGN_H to Aligns.END)))
        )
        assertEquals(100f, LayoutEngine.layout(screenOf(c)).f("a").x, 0.01f)
    }

    @Test
    fun marginsShrinkMatchParentAndOffsetChildren() {
        val c = box(
            "c", ChildLayout.COLUMN, 0f, 0f, 200f, 300f,
            children = listOf(
                el(
                    "a", w = SizeSpec(SizeMode.MATCH_PARENT, 100f),
                    props = mapOf(
                        PropertyKeys.MARGIN_START to "10", PropertyKeys.MARGIN_END to "20",
                        PropertyKeys.MARGIN_TOP to "7"
                    )
                )
            )
        )
        val a = LayoutEngine.layout(screenOf(c)).f("a")
        assertEquals(170f, a.w, 0.01f)
        assertEquals(10f, a.x, 0.01f)
        assertEquals(7f, a.y, 0.01f)
    }

    @Test
    fun fractionIsPercentOfAvailableSpace() {
        val c = box(
            "c", ChildLayout.COLUMN, 0f, 0f, 200f, 300f,
            children = listOf(el("a", w = SizeSpec(SizeMode.FRACTION, 50f)))
        )
        assertEquals(100f, LayoutEngine.layout(screenOf(c)).f("a").w, 0.01f)
    }

    @Test
    fun wrapContentContainerFitsItsChildren() {
        val c = UiElement(
            id = "c",
            type = ElementTypes.CONTAINER,
            layout = ElementLayout(0f, 0f, SizeSpec(SizeMode.WRAP_CONTENT, 10f), SizeSpec(SizeMode.WRAP_CONTENT, 10f)),
            childLayout = ChildLayout.COLUMN,
            properties = mapOf(
                PropertyKeys.PADDING_START to "5", PropertyKeys.PADDING_END to "5",
                PropertyKeys.PADDING_TOP to "5", PropertyKeys.PADDING_BOTTOM to "5",
                PropertyKeys.SPACING to "4"
            ),
            children = listOf(
                el("a", SizeSpec.fixed(100f), SizeSpec.fixed(40f)),
                el("b", SizeSpec.fixed(80f), SizeSpec.fixed(60f))
            )
        )
        val f = LayoutEngine.layout(screenOf(c)).f("c")
        assertEquals(114f, f.h, 0.01f) // 40 + 60 + 4 + 10
        assertEquals(110f, f.w, 0.01f) // 100 + 10
    }

    // ---- الصف والوزن والتوزيع ----

    @Test
    fun rowDividesRemainingSpaceByWeight() {
        val row = box(
            "r", ChildLayout.ROW, 10f, 0f, 300f, 100f,
            children = listOf(
                el("a", w = SizeSpec.fixed(50f), props = mapOf(PropertyKeys.WEIGHT to "1")),
                el("b", w = SizeSpec.fixed(50f), props = mapOf(PropertyKeys.WEIGHT to "2")),
                el("c", w = SizeSpec.fixed(60f))
            )
        )
        val r = LayoutEngine.layout(screenOf(row))
        assertEquals(80f, r.f("a").w, 0.01f)
        assertEquals(160f, r.f("b").w, 0.01f)
        assertEquals(60f, r.f("c").w, 0.01f)
        assertEquals(10f, r.f("a").x, 0.01f)
        assertEquals(90f, r.f("b").x, 0.01f)
        assertEquals(250f, r.f("c").x, 0.01f)
    }

    @Test
    fun rowArrangementDistributesFreeSpace() {
        fun xs(arrangement: String): List<Float> {
            val row = box(
                "r", ChildLayout.ROW, 0f, 0f, 300f, 100f,
                props = mapOf(PropertyKeys.ARRANGEMENT to arrangement),
                children = listOf(el("a", w = SizeSpec.fixed(50f)), el("b", w = SizeSpec.fixed(50f)))
            )
            val r = LayoutEngine.layout(screenOf(row))
            return listOf(r.f("a").x, r.f("b").x)
        }
        assertEquals(listOf(0f, 50f), xs(Arrangements.START))
        assertEquals(listOf(100f, 150f), xs(Arrangements.CENTER))
        assertEquals(listOf(200f, 250f), xs(Arrangements.END))
        assertEquals(listOf(0f, 250f), xs(Arrangements.BETWEEN))
        val evenly = xs(Arrangements.EVENLY)
        assertEquals(66.667f, evenly[0], 0.01f)
        assertEquals(66.667f + 50f + 66.667f, evenly[1], 0.01f)
    }

    @Test
    fun rowVerticalAlignmentUsesChildAlignV() {
        val row = box(
            "r", ChildLayout.ROW, 0f, 0f, 300f, 100f,
            props = mapOf(PropertyKeys.CHILD_ALIGN_V to Aligns.CENTER),
            children = listOf(el("a", h = SizeSpec.fixed(40f)))
        )
        assertEquals(30f, LayoutEngine.layout(screenOf(row)).f("a").y, 0.01f)
    }

    // ---- الصندوق ----

    @Test
    fun boxAlignsEachChildAndStacksThem() {
        val b = box(
            "b", ChildLayout.BOX, 0f, 0f, 200f, 100f,
            props = mapOf(PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER, PropertyKeys.CHILD_ALIGN_V to Aligns.END),
            children = listOf(
                el("a", SizeSpec.fixed(50f), SizeSpec.fixed(20f)),
                el("c", SizeSpec.fixed(50f), SizeSpec.fixed(20f), props = mapOf(PropertyKeys.SELF_ALIGN_V to Aligns.START))
            )
        )
        val r = LayoutEngine.layout(screenOf(b))
        assertEquals(75f, r.f("a").x, 0.01f)
        assertEquals(80f, r.f("a").y, 0.01f)
        assertEquals(0f, r.f("c").y, 0.01f)
        assertEquals(listOf("b", "a", "c"), r.paintOrder)
    }

    // ---- تخطيط جذر الشاشة ----

    @Test
    fun screenRootLayoutArrangesTopLevelElements() {
        val screen = UiScreen(
            id = "s", name = "ش", width = 360f, height = 640f,
            elements = listOf(el("a"), el("b")),
            childLayout = ChildLayout.COLUMN,
            properties = mapOf(
                PropertyKeys.PADDING_START to "16", PropertyKeys.PADDING_TOP to "16",
                PropertyKeys.SPACING to "10"
            )
        )
        val r = LayoutEngine.layout(screen)
        assertEquals(16f, r.f("a").x, 0.01f)
        assertEquals(16f, r.f("a").y, 0.01f)
        assertEquals(66f, r.f("b").y, 0.01f)
        assertTrue(r.freeIds.isEmpty())
        assertEquals(ChildLayout.COLUMN, r.parentLayoutOf("a"))
    }

    // ---- التثبيت عند تغيّر حجم الشاشة ----

    @Test
    fun pinsControlFreeElementsWhenPreviewSizeChanges() {
        val end = el("end", SizeSpec.fixed(40f), SizeSpec.fixed(40f), x = 300f, y = 10f, props = mapOf(PropertyKeys.PIN_H to Pins.END))
        val center = el("center", SizeSpec.fixed(100f), x = 20f, props = mapOf(PropertyKeys.PIN_H to Pins.CENTER))
        val stretch = el("stretch", SizeSpec.fixed(320f), x = 20f, props = mapOf(PropertyKeys.PIN_H to Pins.STRETCH))
        val scale = el("scale", SizeSpec.fixed(72f), x = 36f, props = mapOf(PropertyKeys.PIN_H to Pins.SCALE))
        val start = el("start", x = 10f)
        val screen = screenOf(end, center, stretch, scale, start)

        val same = LayoutEngine.layout(screen)
        assertEquals(300f, same.f("end").x, 0.01f)

        val wide = LayoutEngine.layout(screen, 480f, 640f)
        assertEquals(420f, wide.f("end").x, 0.01f)
        assertEquals(80f, wide.f("center").x, 0.01f)
        assertEquals(20f, wide.f("stretch").x, 0.01f)
        assertEquals(440f, wide.f("stretch").w, 0.01f)
        assertEquals(48f, wide.f("scale").x, 0.01f)
        assertEquals(96f, wide.f("scale").w, 0.01f)
        assertEquals(10f, wide.f("start").x, 0.01f)
        assertEquals(100f, wide.f("start").w, 0.01f)
    }

    @Test
    fun verticalPinsWorkIndependently() {
        val bottom = el("b", SizeSpec.fixed(100f), SizeSpec.fixed(50f), x = 0f, y = 580f, props = mapOf(PropertyKeys.PIN_V to Pins.END))
        val tall = LayoutEngine.layout(screenOf(bottom), 360f, 900f)
        // الفجوة تحت العنصر 640-630=10 تبقى ثابتة
        assertEquals(840f, tall.f("b").y, 0.01f)
    }

    @Test
    fun pinsApplyInsideNestedFreeContainersRelativeToTheirOwnBaseSize() {
        // حاوية تملأ العرض (360 عند التصميم)، وفيها زر ينتهي عند 300 فتبقى فجوة النهاية 60 ثابتة
        val inner = el("btn", SizeSpec.fixed(50f), SizeSpec.fixed(30f), x = 250f, y = 5f, props = mapOf(PropertyKeys.PIN_H to Pins.END))
        val outer = UiElement(
            id = "o",
            type = ElementTypes.CONTAINER,
            layout = ElementLayout(0f, 0f, SizeSpec(SizeMode.MATCH_PARENT, 300f), SizeSpec.fixed(100f)),
            children = listOf(inner)
        )
        val wide = LayoutEngine.layout(screenOf(outer), 500f, 640f)
        assertEquals(500f, wide.f("o").w, 0.01f)
        assertEquals(390f, wide.f("btn").x, 0.01f) // 500 - 60 - 50
    }

    // ---- أدوات المحرر ----

    @Test
    fun containerAtFindsDeepestAndHonoursExclusion() {
        val inner = box("in", ChildLayout.ABSOLUTE, 10f, 10f, 50f, 50f)
        val outer = box("out", ChildLayout.ABSOLUTE, 0f, 0f, 200f, 200f, children = listOf(inner))
        val r = LayoutEngine.layout(screenOf(outer))
        assertEquals("in", LayoutEngine.containerAt(r, 20f, 20f))
        assertEquals("out", LayoutEngine.containerAt(r, 100f, 100f))
        assertNull(LayoutEngine.containerAt(r, 300f, 300f))
        assertEquals("out", LayoutEngine.containerAt(r, 20f, 20f, exclude = setOf("in")))
    }

    @Test
    fun dropIndexCountsSiblingsBeforeThePoint() {
        val c = box(
            "c", ChildLayout.COLUMN, 0f, 0f, 200f, 300f,
            props = mapOf(PropertyKeys.SPACING to "8"),
            children = listOf(el("a", h = SizeSpec.fixed(40f)), el("b", h = SizeSpec.fixed(60f)))
        )
        val r = LayoutEngine.layout(screenOf(c))
        val siblings = listOf("a", "b")
        assertEquals(0, LayoutEngine.dropIndex(r, siblings, true, 10f, 5f))
        assertEquals(1, LayoutEngine.dropIndex(r, siblings, true, 10f, 60f))
        assertEquals(2, LayoutEngine.dropIndex(r, siblings, true, 10f, 250f))
    }

    @Test
    fun layoutIsDeterministicAndNeverNaN() {
        val c = box(
            "c", ChildLayout.ROW, 0f, 0f, 0f, 0f,
            children = listOf(el("a", w = SizeSpec(SizeMode.FRACTION, 50f)), el("b", props = mapOf(PropertyKeys.WEIGHT to "3")))
        )
        val r = LayoutEngine.layout(screenOf(c), 320f, 568f)
        r.frames.values.forEach { f ->
            assertFalse(f.x.isNaN() || f.y.isNaN() || f.w.isNaN() || f.h.isNaN())
            assertTrue(f.w >= CanvasMath.MIN_ELEMENT_SIZE && f.h >= CanvasMath.MIN_ELEMENT_SIZE)
        }
        assertEquals(r.frames, LayoutEngine.layout(screenOf(c), 320f, 568f).frames)
    }
}
