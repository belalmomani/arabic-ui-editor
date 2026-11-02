package com.arabicuieditor.data

import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import com.arabicuieditor.ui.editor.canvas.Prototype
import org.junit.Assert.assertEquals
import org.junit.Test

class LongPressTest {
    private fun btn() = UiElement(
        id = "b", type = ElementTypes.BUTTON,
        layout = ElementLayout(10f, 10f, SizeSpec.fixed(100f), SizeSpec.fixed(40f))
    )

    @Test
    fun clickAndLongPressAreIndependent() {
        var e = Interactions.withClickAction(btn(), ClickAction.Back)
        e = Interactions.withAction(e, Interactions.TRIGGER_LONG_PRESS, ClickAction.Navigate("s2"))
        assertEquals(ClickAction.Back, Interactions.clickAction(e))
        assertEquals(ClickAction.Navigate("s2"), Interactions.actionFor(e, Interactions.TRIGGER_LONG_PRESS))
        e = Interactions.withAction(e, Interactions.TRIGGER_LONG_PRESS, ClickAction.None)
        assertEquals(ClickAction.Back, Interactions.clickAction(e))
        assertEquals(ClickAction.None, Interactions.actionFor(e, Interactions.TRIGGER_LONG_PRESS))
    }

    @Test
    fun prototypeResolvesByTrigger() {
        val e = Interactions.withAction(btn(), Interactions.TRIGGER_LONG_PRESS, ClickAction.Navigate("s2"))
        val screen = UiScreen("s1", "أ", 300f, 400f, listOf(e))
        val layout = LayoutEngine.layout(screen)
        assertEquals(ClickAction.None, Prototype.actionAt(screen, layout, 50f, 30f))
        assertEquals(
            ClickAction.Navigate("s2"),
            Prototype.actionAt(screen, layout, 50f, 30f, Interactions.TRIGGER_LONG_PRESS)
        )
    }
}
