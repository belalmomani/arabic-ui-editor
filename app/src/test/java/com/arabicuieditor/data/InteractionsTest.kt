package com.arabicuieditor.data

import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.EventBinding
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.newProject
import com.arabicuieditor.data.storage.ProjectJsonCodec
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import com.arabicuieditor.ui.editor.canvas.Prototype
import com.arabicuieditor.ui.editor.canvas.PrototypeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionsTest {
    private fun el(
        id: String, x: Float, y: Float, w: Float = 100f, h: Float = 40f,
        type: String = ElementTypes.BUTTON,
        props: Map<String, String> = emptyMap(),
        events: List<EventBinding> = emptyList(),
        children: List<UiElement> = emptyList()
    ) = UiElement(
        id = id, type = type,
        layout = ElementLayout(x, y, SizeSpec.fixed(w), SizeSpec.fixed(h)),
        properties = props, events = events, children = children
    )

    private fun screen(id: String, vararg els: UiElement) = UiScreen(id = id, name = id, elements = els.toList())

    private val screens = listOf(screen("a"), screen("b"), screen("c"))

    @Test fun noEventsMeansNone() = assertEquals(ClickAction.None, Interactions.clickAction(el("x", 0f, 0f)))

    @Test fun navigateRoundTrip() {
        val e = Interactions.withClickAction(el("x", 0f, 0f), ClickAction.Navigate("b"))
        assertEquals(ClickAction.Navigate("b"), Interactions.clickAction(e))
        assertEquals(1, e.events.size)
    }

    @Test fun backRoundTripAndReplace() {
        var e = Interactions.withClickAction(el("x", 0f, 0f), ClickAction.Navigate("b"))
        e = Interactions.withClickAction(e, ClickAction.Back)
        assertEquals(ClickAction.Back, Interactions.clickAction(e))
        assertEquals(1, e.events.size)
        e = Interactions.withClickAction(e, ClickAction.None)
        assertTrue(e.events.isEmpty())
    }

    @Test fun otherEventsAreKept() {
        val other = EventBinding("ev-other", "long_press", "navigate", mapOf("screen" to "c"))
        val e = Interactions.withClickAction(el("x", 0f, 0f, events = listOf(other)), ClickAction.Back)
        assertTrue(e.events.contains(other))
        assertEquals(ClickAction.Back, Interactions.clickAction(e))
    }

    @Test fun targetValidity() {
        assertTrue(Interactions.isTargetValid(screens, ClickAction.Navigate("a")))
        assertFalse(Interactions.isTargetValid(screens, ClickAction.Navigate("gone")))
        assertFalse(Interactions.isTargetValid(screens, ClickAction.Navigate("")))
        assertTrue(Interactions.isTargetValid(screens, ClickAction.Back))
    }

    @Test fun survivesJsonRoundTrip() {
        val button = Interactions.withClickAction(el("btn", 10f, 10f), ClickAction.Navigate("b"))
        val p = newProject("p", "a").let { it.copy(screens = listOf(screen("a", button), screen("b"))) }
        val back = ProjectJsonCodec.decode(ProjectJsonCodec.encode(p))
        val e = back.screens[0].elements[0]
        assertEquals(ClickAction.Navigate("b"), Interactions.clickAction(e))
    }

    // ---- حالة التشغيل ----

    @Test fun navigatePushesHistoryAndBackPops() {
        var s = PrototypeState("a")
        s = s.apply(ClickAction.Navigate("b"), screens)
        s = s.apply(ClickAction.Navigate("c"), screens)
        assertEquals("c", s.current)
        assertEquals(listOf("a", "b"), s.history)
        s = s.apply(ClickAction.Back, screens)
        assertEquals("b", s.current)
        s = s.apply(ClickAction.Back, screens)
        assertEquals("a", s.current)
        assertEquals(s, s.apply(ClickAction.Back, screens))
    }

    @Test fun selfAndMissingNavigationDoNothing() {
        val s = PrototypeState("a")
        assertEquals(s, s.apply(ClickAction.Navigate("a"), screens))
        assertEquals(s, s.apply(ClickAction.Navigate("gone"), screens))
        assertEquals(s, s.apply(ClickAction.None, screens))
    }

    // ---- النقر على العناصر ----

    private fun hit(sc: UiScreen, x: Float, y: Float) =
        Prototype.actionAt(sc, LayoutEngine.layout(sc), x, y)

    private fun nav(id: String) = listOf(EventBinding("e-$id", "click", "navigate", mapOf("screen" to id)))

    @Test fun tapOnButtonNavigates() {
        val sc = screen("a", el("btn", 10f, 10f, events = nav("b")))
        assertEquals(ClickAction.Navigate("b"), hit(sc, 20f, 20f))
        assertEquals(ClickAction.None, hit(sc, 300f, 500f))
    }

    @Test fun topNonClickableDoesNotBlockLowerButton() {
        val sc = screen(
            "a",
            el("btn", 10f, 10f, events = nav("b")),
            el("label", 10f, 10f, type = ElementTypes.TEXT)
        )
        assertEquals(ClickAction.Navigate("b"), hit(sc, 20f, 20f))
    }

    @Test fun topClickableWinsOverLower() {
        val sc = screen(
            "a",
            el("low", 10f, 10f, events = nav("b")),
            el("high", 10f, 10f, events = nav("c"))
        )
        assertEquals(ClickAction.Navigate("c"), hit(sc, 20f, 20f))
    }

    @Test fun hiddenElementIsNotTappable() {
        val sc = screen("a", el("btn", 10f, 10f, props = mapOf(PropertyKeys.VISIBLE to "false"), events = nav("b")))
        assertEquals(ClickAction.None, hit(sc, 20f, 20f))
    }

    @Test fun childTapUsesParentAction() {
        val child = el("child", 5f, 5f, 20f, 20f, type = ElementTypes.TEXT)
        val card = el("card", 10f, 10f, 200f, 100f, type = ElementTypes.CARD, events = nav("c"), children = listOf(child))
        val sc = screen("a", card)
        assertEquals(ClickAction.Navigate("c"), hit(sc, 20f, 20f))
    }
}
