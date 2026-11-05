package com.arabicuieditor.data

import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.script.ScriptEngine
import com.arabicuieditor.data.script.Step
import com.arabicuieditor.data.script.Steps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepsTest {
    private val base = mapOf(
        "عنوان" to mapOf("text" to "مرحبا", "visible" to "true", "textColor" to "#000000", "bgColor" to "#FFFFFF")
    )

    private fun run(steps: List<Step>, vars: Map<String, Any> = emptyMap()) =
        ScriptEngine.run(Steps.toJs(steps), vars, base)

    @Test
    fun encodeDecodeRoundTrip() {
        val s = listOf(Step(Steps.INC, "عداد", "2"), Step(Steps.TEXT, "عنوان", "قال \"نعم\"\nسطر"), Step(Steps.BACK))
        assertEquals(s, Steps.decode(Steps.encode(s)))
        assertTrue(Steps.decode("not json").isEmpty())
    }

    @Test
    fun incrementAndDecrementPersist() {
        val a = run(listOf(Step(Steps.INC, "n", "1")))
        val b = run(listOf(Step(Steps.INC, "n", "1"), Step(Steps.INC, "n", "5"), Step(Steps.DEC, "n", "2")), a.vars)
        assertNull(b.error)
        assertEquals(5.0, b.vars["n"] as Double, 0.0)
    }

    @Test
    fun setVarKeepsNumbersAndText() {
        val r = run(listOf(Step(Steps.SET_VAR, "a", "7"), Step(Steps.SET_VAR, "b", "مرحبا"), Step(Steps.SET_VAR, "c", "true")))
        assertEquals(7.0, r.vars["a"] as Double, 0.0)
        assertEquals("مرحبا", r.vars["b"])
        assertEquals(true, r.vars["c"])
    }

    @Test
    fun conditionControlsOnlyNextStep() {
        val steps = listOf(
            Step(Steps.IF, "n", ">=|3"), Step(Steps.SET_VAR, "big", "1"),
            Step(Steps.SET_VAR, "always", "1")
        )
        val low = run(steps, mapOf("n" to 2.0))
        assertNull(low.vars["big"]); assertEquals(1.0, low.vars["always"] as Double, 0.0)
        val high = run(steps, mapOf("n" to 3.0))
        assertEquals(1.0, high.vars["big"] as Double, 0.0)
    }

    @Test
    fun textConditionAndMissingVar() {
        val steps = listOf(Step(Steps.IF, "mode", "==|داكن"), Step(Steps.TOAST, "", "داكن"))
        assertEquals("داكن", run(steps, mapOf("mode" to "داكن")).toast)
        assertNull(run(steps, mapOf("mode" to "فاتح")).toast)
        assertNull(run(steps).toast)
    }

    @Test
    fun elementStepsProduceOverrides() {
        val r = run(listOf(
            Step(Steps.TEXT, "عنوان", "تم"), Step(Steps.HIDE, "عنوان"),
            Step(Steps.COLOR, "عنوان", "#E53935"), Step(Steps.BG, "عنوان", "#43A047")
        ))
        val o = r.overrides.getValue("عنوان")
        assertEquals("تم", o["text"]); assertEquals("false", o["visible"])
        assertEquals("#E53935", o["textColor"]); assertEquals("#43A047", o["bgColor"])
        val t = ScriptEngine.run(Steps.toJs(listOf(Step(Steps.TOGGLE, "عنوان"))), emptyMap(), base)
        assertEquals("false", t.overrides.getValue("عنوان")["visible"])
    }

    @Test
    fun navigationSteps() {
        val r = run(listOf(Step(Steps.TOAST, "", "x"), Step(Steps.NAV, "الرئيسية"), Step(Steps.BACK)))
        assertEquals("الرئيسية", r.navigateTo); assertTrue(r.back); assertEquals("x", r.toast)
    }

    @Test
    fun quotesInValuesCannotInjectCode() {
        val r = run(listOf(Step(Steps.TOAST, "", "\"); vars.hacked = 1; (\"")))
        assertNull(r.vars["hacked"])
        assertEquals("\"); vars.hacked = 1; (\"", r.toast)
    }

    @Test
    fun blocksActionRoundTripsThroughEvents() {
        val steps = listOf(Step(Steps.INC, "n", "1"))
        val e = Interactions.withClickAction(UiElement(id = "b", type = ElementTypes.BUTTON), ClickAction.Blocks(steps))
        assertEquals(ClickAction.Blocks(steps), Interactions.clickAction(e))
        assertEquals(1, e.events.size)
    }
}
