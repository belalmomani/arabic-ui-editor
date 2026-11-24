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

    @Test
    fun topMostSkipsChildrenOfSelectedParents() {
        val a = com.arabicuieditor.data.model.newElement(com.arabicuieditor.data.model.ElementTypes.BUTTON, 0f, 0f, "a")
        val g = com.arabicuieditor.data.model.newElement(com.arabicuieditor.data.model.ElementTypes.CONTAINER, 0f, 0f, "g").copy(children = listOf(a))
        val b = com.arabicuieditor.data.model.newElement(com.arabicuieditor.data.model.ElementTypes.BUTTON, 0f, 0f, "b")
        val roots = listOf(g, b)
        assertEquals(listOf(g.id, b.id), ElementOps.topMost(roots, setOf(a.id, g.id, b.id)).map { it.id })
        assertEquals(listOf(a.id), ElementOps.topMost(roots, setOf(a.id)).map { it.id })
        assertTrue(ElementOps.topMost(roots, emptySet()).isEmpty())
    }

    private fun moreCard(): List<UiElement> =
        com.arabicuieditor.data.model.ComponentLibrary.presets.first { it.id == "pro_news_more" }.build { "نص" }

    private fun flat(list: List<UiElement>) = com.arabicuieditor.data.model.ElementTree.flatten(list)

    @Test
    fun newsMorePresetTogglesItsOwnDetail() {
        val card = moreCard()
        val all = flat(card)
        val detail = all.first { it.properties[PropertyKeys.NAME] == "more_text" }
        assertEquals("false", detail.properties[PropertyKeys.VISIBLE])
        val btn = all.first { it.properties[PropertyKeys.NAME] == "more_btn" }
        val steps = com.arabicuieditor.data.model.Interactions.clickAction(btn) as com.arabicuieditor.data.model.ClickAction.Blocks
        assertEquals(listOf("show" to "more_text", "hide" to "more_btn"), steps.steps.map { it.kind to it.a })
    }

    @Test
    fun secondCopyOfPresetGetsItsOwnTargets() {
        val first = ElementOps.relinkBlockTargets(emptyList(), moreCard())
        val second = ElementOps.relinkBlockTargets(flat(first), moreCard())
        val all = flat(second)
        assertTrue(all.any { it.properties[PropertyKeys.NAME] == "more_text_2" })
        val btn = all.first { it.properties[PropertyKeys.NAME] == "more_btn_2" }
        val steps = (com.arabicuieditor.data.model.Interactions.clickAction(btn) as com.arabicuieditor.data.model.ClickAction.Blocks).steps
        assertEquals(listOf("more_text_2", "more_btn_2"), steps.map { it.a })
        // النسخة الأولى لم تتغير.
        assertTrue(flat(first).any { it.properties[PropertyKeys.NAME] == "more_text" })
    }

    private fun preset(id: String) = com.arabicuieditor.data.model.ComponentLibrary.presets.first { it.id == id }.build { "نص" }

    @Test
    fun controlPresetsToggleTheirOwnParts() {
        val cb = flat(preset("pro_checkbox_row"))
        val box = cb.first { com.arabicuieditor.data.model.Interactions.clickAction(it) is com.arabicuieditor.data.model.ClickAction.Blocks }
        val st = (com.arabicuieditor.data.model.Interactions.clickAction(box) as com.arabicuieditor.data.model.ClickAction.Blocks).steps
        assertEquals(listOf("toggle" to "chk_mark"), st.map { it.kind to it.a })
        assertEquals("false", cb.first { it.properties[PropertyKeys.NAME] == "chk_mark" }.properties[PropertyKeys.VISIBLE])

        val sw = flat(preset("pro_switch_row"))
        assertEquals("false", sw.first { it.properties[PropertyKeys.NAME] == "sw_on" }.properties[PropertyKeys.VISIBLE])
        val on = sw.first { it.properties[PropertyKeys.NAME] == "sw_on" }
        assertEquals(listOf("sw_off", "sw_on"), (com.arabicuieditor.data.model.Interactions.clickAction(on) as com.arabicuieditor.data.model.ClickAction.Blocks).steps.map { it.a })

        val dd = flat(preset("pro_dropdown"))
        val opt = dd.first { (com.arabicuieditor.data.model.Interactions.clickAction(it) as? com.arabicuieditor.data.model.ClickAction.Blocks)?.steps?.firstOrNull()?.kind == "text" }
        val ostep = (com.arabicuieditor.data.model.Interactions.clickAction(opt) as com.arabicuieditor.data.model.ClickAction.Blocks).steps
        assertEquals(listOf("dd_btn" to "text", "dd_opts" to "hide").map { it.second to it.first }, ostep.map { it.kind to it.a })
    }

    @Test
    fun secondSwitchGetsItsOwnTargets() {
        val first = ElementOps.relinkBlockTargets(emptyList(), preset("pro_switch_row"))
        val second = flat(ElementOps.relinkBlockTargets(flat(first), preset("pro_switch_row")))
        val on = second.first { it.properties[PropertyKeys.NAME] == "sw_on_2" }
        assertEquals(listOf("sw_off_2", "sw_on_2"), (com.arabicuieditor.data.model.Interactions.clickAction(on) as com.arabicuieditor.data.model.ClickAction.Blocks).steps.map { it.a })
    }

    @Test
    fun templateCopiesOfSameComponentHaveTheirOwnTargets() {
        val t = com.arabicuieditor.data.model.Templates.all.first { it.id == "tpl_souq_admin" }
        val screens = com.arabicuieditor.data.model.Templates.build(t, emptyList(), { "t$it" })
        val review = screens[1]
        val cards = flat(review.elements).filter { it.properties[PropertyKeys.NAME].orEmpty().startsWith("adm_card") }
        assertEquals(3, cards.size)
        assertEquals(3, cards.map { it.properties[PropertyKeys.NAME] }.toSet().size)
        val hides = flat(review.elements).mapNotNull { (com.arabicuieditor.data.model.Interactions.clickAction(it) as? com.arabicuieditor.data.model.ClickAction.Blocks)?.steps }
            .flatten().filter { it.kind == "hide" }.map { it.a }
        assertEquals(6, hides.size)
        assertEquals(3, hides.toSet().size)
    }

    @Test
    fun cartAndToastLogicIsAttachedToStoreButtons() {
        val card = flat(com.arabicuieditor.data.model.ComponentLibrary.presets.first { it.id == "pro_product_card" }.build { "x" })
        val steps = card.mapNotNull { (com.arabicuieditor.data.model.Interactions.clickAction(it) as? com.arabicuieditor.data.model.ClickAction.Blocks)?.steps }.flatten()
        assertEquals(listOf("inc", "toast"), steps.map { it.kind })
        assertEquals("cart", steps.first().a)
    }
}
