package com.arabicuieditor.data.script

import org.json.JSONArray
import org.json.JSONObject

/**
 * خطوة منطق بلا كتابة كود: النوع + حقلان (a, b) معناهما حسب النوع.
 * تُترجم إلى جافا سكريبت وتُنفَّذ بمحرك المعاينة نفسه ([ScriptEngine]).
 */
data class Step(val kind: String, val a: String = "", val b: String = "")

object Steps {
    const val INC = "inc"          // a = متغير، b = المقدار
    const val DEC = "dec"
    const val SET_VAR = "setvar"   // a = متغير، b = القيمة
    const val IF = "if"            // a = متغير، b = "<عامل>|<قيمة>"؛ يحكم الخطوة التالية فقط
    const val TEXT = "text"        // a = عنصر، b = النص (يقبل {{x}})
    const val SHOW = "show"        // a = عنصر
    const val HIDE = "hide"
    const val TOGGLE = "toggle"
    const val COLOR = "color"      // a = عنصر، b = لون hex للنص
    const val BG = "bg"            // a = عنصر، b = لون hex للخلفية
    const val TOAST = "toast"      // b = النص
    const val NAV = "nav"          // a = اسم الشاشة
    const val BACK = "back"

    val kinds = listOf(INC, DEC, SET_VAR, IF, TEXT, SHOW, HIDE, TOGGLE, COLOR, BG, TOAST, NAV, BACK)
    val ops = listOf("==", "!=", ">", "<", ">=", "<=")
    val palette = listOf("#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#8E24AA", "#212121", "#FFFFFF")
    const val MAX_STEPS = 30

    fun usesElement(kind: String) = kind in setOf(TEXT, SHOW, HIDE, TOGGLE, COLOR, BG)
    fun usesVar(kind: String) = kind in setOf(INC, DEC, SET_VAR, IF)

    /** القيمة الافتراضية عند إضافة خطوة جديدة. */
    fun newStep(kind: String, firstElement: String = "", firstScreen: String = "", firstVar: String = ""): Step = when (kind) {
        INC, DEC -> Step(kind, firstVar.ifEmpty { "عداد" }, "1")
        SET_VAR -> Step(kind, firstVar.ifEmpty { "عداد" }, "0")
        IF -> Step(kind, firstVar.ifEmpty { "عداد" }, "==|0")
        TEXT -> Step(kind, firstElement, "نص جديد")
        SHOW, HIDE, TOGGLE -> Step(kind, firstElement)
        COLOR, BG -> Step(kind, firstElement, palette[0])
        TOAST -> Step(kind, "", "تم")
        NAV -> Step(kind, firstScreen)
        else -> Step(kind)
    }

    fun encode(steps: List<Step>): String {
        val arr = JSONArray()
        steps.forEach { arr.put(JSONObject().put("k", it.kind).put("a", it.a).put("b", it.b)) }
        return arr.toString()
    }

    fun decode(text: String): List<Step> = try {
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val k = o.optString("k")
            if (k in kinds) Step(k, o.optString("a"), o.optString("b")) else null
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun q(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            ' ' -> sb.append("\\u2028")
            ' ' -> sb.append("\\u2029")
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    private fun v(name: String) = "vars[${q(name)}]"
    private fun el(name: String) = "\$(${q(name)})"

    private fun literal(b: String): String = when {
        b.toDoubleOrNull() != null && b.trim().toDoubleOrNull()?.isFinite() == true -> b.trim().toDouble().let {
            if (it == Math.floor(it) && Math.abs(it) < 1e15) it.toLong().toString() else it.toString()
        }
        b == "true" || b == "false" -> b
        else -> q(b)
    }

    private fun num(b: String): String = b.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.toString() ?: "1"

    private fun condition(s: Step): String {
        val (opRaw, value) = s.b.split("|", limit = 2).let { (it.getOrNull(0) ?: "==") to (it.getOrNull(1) ?: "") }
        val op = if (opRaw in ops) opRaw else "=="
        val n = value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        return if (n != null) "((Number(${v(s.a)}) || 0) $op $n)"
        else {
            val textOp = if (op == "!=") "!=" else "=="
            "((${v(s.a)} === undefined ? \"\" : String(${v(s.a)})) $textOp ${q(value)})"
        }
    }

    private fun statement(s: Step): String? = when (s.kind) {
        INC -> "${v(s.a)} = (Number(${v(s.a)}) || 0) + ${num(s.b)};"
        DEC -> "${v(s.a)} = (Number(${v(s.a)}) || 0) - ${num(s.b)};"
        SET_VAR -> "${v(s.a)} = ${literal(s.b)};"
        TEXT -> "${el(s.a)}.text = ${q(s.b)};"
        SHOW -> "${el(s.a)}.visible = true;"
        HIDE -> "${el(s.a)}.visible = false;"
        TOGGLE -> "${el(s.a)}.visible = !${el(s.a)}.visible;"
        COLOR -> "${el(s.a)}.color = ${q(s.b)};"
        BG -> "${el(s.a)}.bg = ${q(s.b)};"
        TOAST -> "toast(${q(s.b)});"
        NAV -> "navigate(${q(s.a)});"
        BACK -> "back();"
        else -> null
    }

    /** يحوّل الخطوات إلى جافا سكريبت. خطوة «إذا» تحكم الخطوة التي تليها فقط. */
    fun toJs(steps: List<Step>): String {
        val out = StringBuilder()
        var pending: String? = null
        for (s in steps.take(MAX_STEPS)) {
            if (s.kind == IF) {
                if (s.a.isNotBlank()) pending = condition(s)
                continue
            }
            val st = statement(s) ?: continue
            if (pending != null) out.append("if (").append(pending).append(") { ").append(st).append(" }\n")
            else out.append(st).append('\n')
            pending = null
        }
        return out.toString()
    }

    /** أسماء كل المتغيرات المستعملة في الخطوات (لاقتراحها كشرائح). */
    fun varNames(steps: List<Step>): List<String> =
        steps.filter { usesVar(it.kind) && it.a.isNotBlank() }.map { it.a }.distinct()
}
