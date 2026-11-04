package com.arabicuieditor.data.script

import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.prop
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/** نتيجة تشغيل سكربت واحد: تأثيراته تُطبَّق بعد انتهائه (التشغيل متزامن وقصير). */
data class ScriptOutcome(
    /** متغيرات `vars` بعد التشغيل (أرقام Double ونصوص وقيم منطقية فقط). */
    val vars: Map<String, Any>,
    /** تعديلات وقت التشغيل: اسم العنصر ← (الخاصية ← القيمة). */
    val overrides: Map<String, Map<String, String>>,
    val navigateTo: String? = null,
    val back: Boolean = false,
    val toast: String? = null,
    val error: String? = null
)

/**
 * محرك سكربتات المعاينة (Rhino في وضع المفسّر، بلا وصول إلى جافا). كل تشغيل بنطاق جديد؛
 * الحالة تنتقل عبر [vars] و[overrides] فقط. حدّ زمني يوقف الحلقات اللانهائية.
 *
 * واجهة JS: `vars.x`، `$('اسم').text/.visible/.color/.bg`، `navigate('اسم الشاشة')`، `back()`، `toast('نص')`.
 */
object ScriptEngine {
    const val MAX_CODE = 4000
    const val TIME_LIMIT_MS = 400L

    /** Error (لا RuntimeException) كي لا تلتقطه try/catch داخل السكربت. */
    private class Timeout : Error("تجاوز السكربت الحدّ الزمني")

    private val factory = object : ContextFactory() {
        override fun makeContext(): Context {
            val cx = super.makeContext()
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6
            cx.instructionObserverThreshold = 2000
            cx.setClassShutter { false }
            return cx
        }

        override fun observeInstructionCount(cx: Context, instructionCount: Int) {
            val start = cx.getThreadLocal("start") as? Long ?: return
            if (System.currentTimeMillis() - start > TIME_LIMIT_MS) throw Timeout()
        }
    }

    private const val PRELUDE = """
function DOLLAR(n){return {
 get text(){return __get(n,'text')}, set text(v){__set(n,'text',v)},
 get visible(){return __get(n,'visible')!=='false'}, set visible(v){__set(n,'visible',v?'true':'false')},
 get color(){return __get(n,'textColor')}, set color(v){__set(n,'textColor',v)},
 get bg(){return __get(n,'bgColor')}, set bg(v){__set(n,'bgColor',v)}
}}
"""

    /** مفتاح خاصية JS ← مفتاح خاصية النموذج (مسموح فقط). */
    val allowedKeys = setOf("text", "visible", "textColor", "bgColor")

    /**
     * @param baseProps قيم الخصائص الحالية لكل عنصر باسمه (للقراءة): اسم ← (خاصية ← قيمة).
     * @param screens أسماء الشاشات الصالحة للانتقال (التحقق يتم عند التطبيق).
     */
    fun run(
        code: String,
        vars: Map<String, Any>,
        baseProps: Map<String, Map<String, String>>,
        overrides: Map<String, Map<String, String>> = emptyMap()
    ): ScriptOutcome {
        if (code.isBlank()) return ScriptOutcome(vars, overrides)
        if (code.length > MAX_CODE) return ScriptOutcome(vars, overrides, error = "السكربت أطول من $MAX_CODE حرفًا")
        val ov = overrides.mapValues { it.value.toMutableMap() }.toMutableMap()
        var nav: String? = null
        var back = false
        var toast: String? = null
        var error: String? = null
        var outVars = vars
        val cx = factory.enterContext()
        try {
            cx.putThreadLocal("start", System.currentTimeMillis())
            val scope = cx.initSafeStandardObjects(null, false)
            fun fn(name: String, body: (Array<Any?>) -> Any?) {
                ScriptableObject.putProperty(scope, name, object : BaseFunction() {
                    override fun call(cx: Context, s: Scriptable, t: Scriptable?, args: Array<Any?>): Any? =
                        body(args) ?: Context.getUndefinedValue()
                })
            }
            fun str(a: Array<Any?>, i: Int): String = if (i < a.size) Context.toString(a[i]) else ""
            fn("__get") { a ->
                val n = str(a, 0); val k = str(a, 1)
                ov[n]?.get(k) ?: baseProps[n]?.get(k) ?: ""
            }
            fn("__set") { a ->
                val n = str(a, 0); val k = str(a, 1)
                if (k in allowedKeys && baseProps.containsKey(n)) ov.getOrPut(n) { mutableMapOf() }[k] = str(a, 2)
                null
            }
            fn("navigate") { a -> nav = str(a, 0); null }
            fn("back") { _ -> back = true; null }
            fn("toast") { a -> toast = str(a, 0).take(200); null }
            val v = cx.newObject(scope)
            vars.forEach { (k, value) -> ScriptableObject.putProperty(v, k, value) }
            ScriptableObject.putProperty(scope, "vars", v)
            cx.evaluateString(scope, PRELUDE.replace("DOLLAR", "$"), "prelude", 1, null)
            try {
                cx.evaluateString(scope, code, "script", 1, null)
            } catch (e: Timeout) {
                error = "تجاوز السكربت الحدّ الزمني (حلقة لا نهائية؟)"
            } catch (e: RhinoException) {
                error = "${e.details()} (سطر ${e.lineNumber()})"
            }
            outVars = readVars(v)
        } catch (e: Throwable) {
            error = e.message ?: "خطأ غير معروف"
        } finally {
            Context.exit()
        }
        return ScriptOutcome(outVars, ov, nav, back, toast, error)
    }

    private fun readVars(v: Scriptable): Map<String, Any> {
        val out = linkedMapOf<String, Any>()
        for (id in ScriptableObject.getPropertyIds(v)) {
            val key = id.toString()
            val value = ScriptableObject.getProperty(v, key)
            when (value) {
                is Number -> out[key] = value.toDouble()
                is Boolean -> out[key] = value
                is CharSequence -> out[key] = value.toString()
                Scriptable.NOT_FOUND, Context.getUndefinedValue(), null -> {}
                else -> out[key] = Context.toString(value)
            }
        }
        return out
    }

    private val TEMPLATE = Regex("""\{\{\s*(?:vars\.)?([\p{L}_][\p{L}\p{N}_]*)\s*\}\}""")

    /** يستبدل `{{x}}` أو `{{vars.x}}` في النص بقيمة المتغير (المجهول يبقى كما هو). */
    fun interpolate(text: String, vars: Map<String, Any>): String {
        if (!text.contains("{{")) return text
        return TEMPLATE.replace(text) { m ->
            val value = vars[m.groupValues[1]] ?: return@replace m.value
            formatValue(value)
        }
    }

    fun formatValue(value: Any): String = when (value) {
        is Double -> if (value == Math.floor(value) && !value.isInfinite() && Math.abs(value) < 1e15) value.toLong().toString() else value.toString()
        else -> value.toString()
    }
}

/** تطبيق حالة السكربت (تعديلات الخصائص + استبدال `{{x}}` في النصوص) على شجرة عناصر؛ دوال نقية. */
object ScriptOverrides {
    fun apply(
        elements: List<UiElement>,
        overrides: Map<String, Map<String, String>>,
        vars: Map<String, Any>
    ): List<UiElement> {
        if (overrides.isEmpty() && vars.isEmpty()) return elements
        return elements.map { e ->
            val name = e.properties[PropertyKeys.NAME].orEmpty()
            var props = e.properties
            overrides[name]?.takeIf { name.isNotEmpty() }?.let { props = props + it }
            props[PropertyKeys.TEXT]?.let { t ->
                val r = ScriptEngine.interpolate(t, vars)
                if (r != t) props = props + (PropertyKeys.TEXT to r)
            }
            e.copy(properties = props, children = apply(e.children, overrides, vars))
        }
    }

    /** خصائص القراءة لكل عنصر مسمّى في الشاشة: اسم ← (خاصية ← قيمة). */
    fun baseProps(elements: List<UiElement>): Map<String, Map<String, String>> {
        val out = linkedMapOf<String, Map<String, String>>()
        ElementTree.flatten(elements).forEach { e ->
            val name = e.properties[PropertyKeys.NAME].orEmpty()
            if (name.isNotEmpty()) {
                out[name] = ScriptEngine.allowedKeys.associateWith { k -> e.prop(k) }
            }
        }
        return out
    }
}
