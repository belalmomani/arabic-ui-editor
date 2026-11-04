package com.arabicuieditor.data

import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.script.ScriptEngine
import com.arabicuieditor.data.script.ScriptOverrides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptEngineTest {
    private val base = mapOf(
        "عنوان" to mapOf("text" to "مرحبا", "visible" to "true", "textColor" to "#000000", "bgColor" to "#FFFFFF")
    )

    @Test
    fun varsPersistAcrossRuns() {
        val a = ScriptEngine.run("vars.count = (vars.count || 0) + 1", emptyMap(), base)
        assertNull(a.error)
        val b = ScriptEngine.run("vars.count = (vars.count || 0) + 1", a.vars, base)
        assertEquals(2.0, b.vars["count"] as Double, 0.0)
    }

    @Test
    fun stringsAndBooleansRoundTrip() {
        val r = ScriptEngine.run("vars.name = 'علي'; vars.ok = true; vars.s = 'a' + 1", emptyMap(), base)
        assertEquals("علي", r.vars["name"])
        assertEquals(true, r.vars["ok"])
        assertEquals("a1", r.vars["s"])
    }

    @Test
    fun elementHandleReadsAndWrites() {
        val r = ScriptEngine.run(
            "var t = \$('عنوان'); vars.old = t.text; t.text = 'تم'; t.visible = false; t.color = '#FF0000'; t.bg = '#00FF00'",
            emptyMap(), base
        )
        assertNull(r.error)
        assertEquals("مرحبا", r.vars["old"])
        val o = r.overrides.getValue("عنوان")
        assertEquals("تم", o["text"])
        assertEquals("false", o["visible"])
        assertEquals("#FF0000", o["textColor"])
        assertEquals("#00FF00", o["bgColor"])
    }

    @Test
    fun readSeesEarlierOverride() {
        val first = ScriptEngine.run("\$('عنوان').text = 'جديد'", emptyMap(), base)
        val second = ScriptEngine.run("vars.seen = \$('عنوان').text", emptyMap(), base, first.overrides)
        assertEquals("جديد", second.vars["seen"])
    }

    @Test
    fun unknownElementIsIgnored() {
        val r = ScriptEngine.run("\$('غير موجود').text = 'x'", emptyMap(), base)
        assertTrue(r.overrides.isEmpty())
    }

    @Test
    fun navigationAndToastAreCollected() {
        val r = ScriptEngine.run("toast('أهلا'); navigate('الرئيسية'); back()", emptyMap(), base)
        assertEquals("أهلا", r.toast)
        assertEquals("الرئيسية", r.navigateTo)
        assertTrue(r.back)
    }

    @Test
    fun syntaxErrorIsReportedNotThrown() {
        val r = ScriptEngine.run("var = ;", mapOf("x" to 1.0), base)
        assertNotNull(r.error)
        assertEquals(1.0, r.vars["x"] as Double, 0.0)
    }

    @Test
    fun runtimeErrorKeepsPartialVars() {
        val r = ScriptEngine.run("vars.a = 1; undefinedFunction();", emptyMap(), base)
        assertNotNull(r.error)
        assertEquals(1.0, r.vars["a"] as Double, 0.0)
    }

    @Test
    fun infiniteLoopIsStopped() {
        val t0 = System.currentTimeMillis()
        val r = ScriptEngine.run("while(true){}", emptyMap(), base)
        assertNotNull(r.error)
        assertTrue(System.currentTimeMillis() - t0 < 5000)
    }

    @Test
    fun tryCatchCannotSwallowTimeout() {
        val t0 = System.currentTimeMillis()
        val r = ScriptEngine.run("try { while(true){} } catch (e) { } finally { }", emptyMap(), base)
        assertNotNull(r.error)
        assertTrue(System.currentTimeMillis() - t0 < 5000)
    }

    @Test
    fun javaIsNotReachable() {
        val r = ScriptEngine.run("vars.x = typeof java; var f = new java.io.File('/'); ", emptyMap(), base)
        assertNotNull(r.error)
        assertFalse(r.vars["x"] == "object")
    }

    @Test
    fun tooLongCodeRejected() {
        val r = ScriptEngine.run("//" + "x".repeat(ScriptEngine.MAX_CODE), emptyMap(), base)
        assertNotNull(r.error)
    }

    @Test
    fun interpolation() {
        val v = mapOf<String, Any>("count" to 3.0, "name" to "سارة", "pi" to 3.5)
        assertEquals("العدد 3", ScriptEngine.interpolate("العدد {{count}}", v))
        assertEquals("سارة / 3.5", ScriptEngine.interpolate("{{ vars.name }} / {{pi}}", v))
        assertEquals("{{unknown}}", ScriptEngine.interpolate("{{unknown}}", v))
        assertEquals("بلا قوالب", ScriptEngine.interpolate("بلا قوالب", v))
    }

    @Test
    fun overridesApplyByNameAndInterpolateText() {
        val el = UiElement(
            id = "1", type = ElementTypes.TEXT,
            properties = mapOf(PropertyKeys.NAME to "عنوان", PropertyKeys.TEXT to "عدد {{count}}")
        )
        val other = UiElement(id = "2", type = ElementTypes.TEXT, properties = mapOf(PropertyKeys.TEXT to "ثابت"))
        val out = ScriptOverrides.apply(
            listOf(el, other),
            mapOf("عنوان" to mapOf("visible" to "false")),
            mapOf("count" to 5.0)
        )
        assertEquals("عدد 5", out[0].properties[PropertyKeys.TEXT])
        assertEquals("false", out[0].properties[PropertyKeys.VISIBLE])
        assertEquals("ثابت", out[1].properties[PropertyKeys.TEXT])
        assertEquals(base.keys, ScriptOverrides.baseProps(listOf(el.copy(properties = mapOf(PropertyKeys.NAME to "عنوان")))).keys)
    }

    @Test
    fun scriptActionRoundTripsThroughEvents() {
        val e = UiElement(id = "b", type = ElementTypes.BUTTON)
        val withIt = Interactions.withClickAction(e, ClickAction.Script("vars.a = 1"))
        assertEquals(ClickAction.Script("vars.a = 1"), Interactions.clickAction(withIt))
        assertEquals(1, withIt.events.size)
        val replaced = Interactions.withClickAction(withIt, ClickAction.Back)
        assertEquals(ClickAction.Back, Interactions.clickAction(replaced))
    }
}
