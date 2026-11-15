package com.arabicuieditor.data

import com.arabicuieditor.data.export.ScriptToKotlin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptToKotlinTest {
    private val env = ScriptToKotlin.Env(
        route = { if (it == "الرئيسية") "screen1" else null },
        initialText = { if (it == "عنوان" || it == "زر") "نص" else null },
        initialVisible = { true },
        soundCall = { if (it == "pop") "playSound(ctx, \"snd_pop\")" else null },
        esc = { it.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") }
    )

    private fun k(code: String) = ScriptToKotlin.convert(code, env)

    @Test
    fun counterAndTextAssignment() {
        val r = k("vars.count = (vars.count || 0) + 1; \$('عنوان').text = 'العدد: ' + vars.count;")
        // «||» بين رقم ومتغير غير مدعوم دلاليًا (bool على رقم) فيُرفض كاملًا لا يُصدَّر خطأً.
        assertNull(r)
        val ok = k("vars.count += 1; \$('عنوان').text = 'العدد: ' + vars.count;")!!
        assertEquals(
            "AppLogic.add(\"count\", 1.0); AppLogic.props[\"عنوان|text\"] = (\"العدد: \" + AppLogic.str(\"count\"))",
            ok.kotlin
        )
        assertEquals(setOf("عنوان"), ok.elements)
    }

    @Test
    fun ifElseAndComparisons() {
        val r = k("if (vars.score >= 10) { toast('فزت'); navigate('الرئيسية'); } else { vars.score++; }")!!
        assertTrue(r.usesToast)
        assertEquals(
            "if ((AppLogic.num(\"score\") >= 10.0)) { Toast.makeText(ctx, \"فزت\", Toast.LENGTH_SHORT).show(); onNavigate(\"screen1\") } " +
                "else { AppLogic.add(\"score\", 1.0) }",
            r.kotlin
        )
    }

    @Test
    fun visibilityAndSound() {
        val r = k("\$('زر').visible = !\$('زر').visible; sound('pop'); back();")!!
        assertEquals(
            "AppLogic.props[\"زر|visible\"] = if ((!AppLogic.visible(\"زر\", true))) \"true\" else \"false\"; " +
                "playSound(ctx, \"snd_pop\"); onBack()",
            r.kotlin
        )
    }

    @Test
    fun stringEqualityUsesStrings() {
        val r = k("if (vars.mode == 'dark') { vars.mode = 'light'; }")!!
        assertEquals("if ((AppLogic.str(\"mode\") == \"dark\")) { AppLogic.vars[\"mode\"] = \"light\" }", r.kotlin)
    }

    @Test
    fun unsupportedConstructsReturnNull() {
        assertNull(k("for (var i = 0; i < 3; i++) { vars.x = i; }"))
        assertNull(k("function f() { return 1; } f();"))
        assertNull(k("vars.x = `a`;"))
        assertNull(k("navigate('غير موجودة');"))
        assertNull(k("\$('مجهول').text = 'a';"))
        assertNull(k("sound('nope');"))
        assertNull(k(""))
        assertNotNull(k("// تعليق\nvars.a = 1; /* آخر */"))
    }
}
