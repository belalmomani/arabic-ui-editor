package com.arabicuieditor.data.ai

import com.arabicuieditor.data.model.Aligns
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.FontWeights
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.ScreenOps
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.newId
import org.json.JSONArray
import org.json.JSONObject

/**
 * «الذكاء الاصطناعي من حسابك المجاني»: يبني نصًّا جاهزًا يلصقه المستخدم في أي شات،
 * ثم يحوّل ردّ الشات (JSON مبسّط) إلى مشروع. لا اتصال بأي خدمة ولا مفتاح API.
 * المحوّل متسامح: يتجاهل ما لا يفهمه ويقيّد القيم بالمدى بدل أن يرفض الرد كله.
 */
object AiProject {
    const val MAX_SCREENS = 12
    const val MAX_ELEMENTS = 400
    private const val MAX_DEPTH = 6

    /** النص الذي ينسخه المستخدم؛ [idea] وصف فكرته بكلماته. */
    fun prompt(idea: String): String = buildString {
        appendLine("أنت مصمّم واجهات تطبيقات. صمّم تطبيقًا بالعربية (واجهات من اليمين إلى اليسار) حسب فكرتي في نهاية الرسالة.")
        appendLine()
        appendLine("أعطني الرد كاملًا على شكل JSON واحد فقط: بلا شرح، وبلا علامات ``` ، وبلا أي نص قبله أو بعده.")
        appendLine()
        appendLine("الصيغة:")
        appendLine("""{"name":"اسم التطبيق","screens":[{"name":"اسم الشاشة","layout":"column","elements":[ ... ]}]}""")
        appendLine()
        appendLine("كل عنصر كائن فيه:")
        appendLine("- type (إلزامي): text أو button أو image أو input أو container أو card أو divider أو spacer أو list")
        appendLine("- text: نص العنصر (للنص والزر)، hint: تلميح حقل الإدخال")
        appendLine("- width و height: \"fill\" (يملأ المتاح) أو \"wrap\" (بحجم المحتوى) أو رقم بوحدة dp")
        appendLine("- size: حجم الخط (10 إلى 40)، bold: true أو false، color: لون النص، bg: لون الخلفية (مثل #23866B)")
        appendLine("- radius: استدارة الزوايا، padding: حشو داخلي، spacing: مسافة بين الأبناء، align: start أو center أو end")
        appendLine("- layout (للـ container والـ card): column أو row أو box، وله children: [عناصر]")
        appendLine("- go: اسم شاشة ينتقل إليها عند النقر (يجب أن يطابق اسم شاشة موجودة تمامًا)، أو back: true للرجوع")
        appendLine()
        appendLine("قواعد:")
        appendLine("- من 2 إلى 6 شاشات، وكل شاشة من 4 إلى 15 عنصرًا. اجعل الواجهة نظيفة ومتناسقة ولونها الأساسي #23866B.")
        appendLine("- اكتب كل النصوص بالعربية الفصحى المبسّطة وبمحتوى واقعي (لا تكتب «نص هنا»).")
        appendLine("- لا تستعمل صورًا حقيقية: استعمل عنصر image ببساطة وسيضع المستخدم الصورة لاحقًا.")
        appendLine("- اربط الأزرار بين الشاشات باستعمال go.")
        appendLine()
        appendLine("مثال مختصر:")
        appendLine(EXAMPLE)
        appendLine()
        append("فكرة التطبيق: ").append(idea.trim().ifEmpty { "(اكتب فكرتك هنا)" })
    }

    private const val EXAMPLE =
        """{"name":"مطعم الواحة","screens":[{"name":"الرئيسية","layout":"column","elements":[{"type":"text","text":"مرحبًا بك في مطعم الواحة","size":22,"bold":true},{"type":"card","layout":"column","padding":12,"spacing":6,"children":[{"type":"image","height":140},{"type":"text","text":"وجبة اليوم: مندي دجاج","size":16}]},{"type":"button","text":"عرض القائمة","width":"fill","go":"القائمة"}]},{"name":"القائمة","layout":"column","elements":[{"type":"text","text":"القائمة","size":20,"bold":true},{"type":"button","text":"رجوع","back":true}]}]}"""

    /** يستخرج أول كائن JSON من ردّ قد يحوي ``` أو شرحًا. */
    fun extractJson(reply: String): JSONObject? {
        val text = reply.trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try { JSONObject(text.substring(start, end + 1)) } catch (_: Exception) { null }
    }

    /** يحوّل ردّ الشات إلى مشروع جديد، أو null إن لم يكن فيه شاشة واحدة صالحة. */
    fun parse(reply: String, now: Long = System.currentTimeMillis()): Project? {
        val root = extractJson(reply) ?: return null
        val screensJson = root.optJSONArray("screens") ?: return null
        val budget = intArrayOf(MAX_ELEMENTS)
        val screens = ArrayList<UiScreen>()
        val nav = ArrayList<Pair<String, String>>() // elementId → اسم الشاشة الهدف (يُحلّ بعد بناء كل الشاشات)
        for (i in 0 until minOf(screensJson.length(), MAX_SCREENS)) {
            val sj = screensJson.optJSONObject(i) ?: continue
            val name = ScreenOps.uniqueName(screens, sj.optString("name").ifBlank { "شاشة ${i + 1}" })
            val elements = parseList(sj.optJSONArray("elements"), 0, budget, nav)
            val layout = when (sj.optString("layout").lowercase()) {
                "absolute" -> ChildLayout.ABSOLUTE
                "row" -> ChildLayout.ROW
                else -> ChildLayout.COLUMN
            }
            val props = if (layout == ChildLayout.ABSOLUTE) emptyMap() else mapOf(
                PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER,
                PropertyKeys.SPACING to "12",
                PropertyKeys.PADDING_START to "16", PropertyKeys.PADDING_TOP to "16",
                PropertyKeys.PADDING_END to "16", PropertyKeys.PADDING_BOTTOM to "16"
            )
            screens += UiScreen(id = newId(), name = name, elements = elements, childLayout = layout, properties = props)
        }
        if (screens.isEmpty()) return null
        val byName = screens.associateBy { it.name }
        val resolved = nav.mapNotNull { (id, target) -> byName[target]?.let { id to it.id } }.toMap()
        val linked = screens.map { s -> s.copy(elements = s.elements.map { link(it, resolved) }) }
        val name = root.optString("name").trim().take(60).ifEmpty { "مشروع بالذكاء الاصطناعي" }
        return Project(id = newId(), name = name, createdAt = now, updatedAt = now, screens = linked)
    }

    private fun link(e: UiElement, targets: Map<String, String>): UiElement {
        val withNav = targets[e.id]?.let { Interactions.withClickAction(e, ClickAction.Navigate(it)) } ?: e
        return withNav.copy(children = withNav.children.map { link(it, targets) })
    }

    private fun parseList(arr: JSONArray?, depth: Int, budget: IntArray, nav: MutableList<Pair<String, String>>): List<UiElement> {
        if (arr == null || depth > MAX_DEPTH) return emptyList()
        val out = ArrayList<UiElement>()
        for (i in 0 until arr.length()) {
            if (budget[0] <= 0) break
            val j = arr.optJSONObject(i) ?: continue
            parseElement(j, depth, budget, nav)?.let { out += it }
        }
        return out
    }

    private fun parseElement(j: JSONObject, depth: Int, budget: IntArray, nav: MutableList<Pair<String, String>>): UiElement? {
        val type = j.optString("type").lowercase().let { if (it in ElementTypes.all) it else return null }
        budget[0]--
        val props = LinkedHashMap<String, String>()
        fun put(key: String, raw: String?) {
            if (raw.isNullOrEmpty()) return
            if (PropertyRegistry.forType(type).none { it.key == key }) return
            PropertyRegistry.normalize(key, raw)?.let { props[key] = it }
        }
        fun num(key: String): String? = if (j.has(key) && !j.isNull(key)) j.opt(key).toString().substringBefore('.') else null

        if (type == ElementTypes.TEXT || type == ElementTypes.BUTTON) put(PropertyKeys.TEXT, j.optString("text").ifEmpty { null })
        if (type == ElementTypes.INPUT) put(PropertyKeys.HINT, j.optString("hint").ifEmpty { j.optString("text") }.ifEmpty { null })
        put(PropertyKeys.FONT_SIZE, num("size"))
        if (j.optBoolean("bold", false)) put(PropertyKeys.FONT_WEIGHT, FontWeights.BOLD)
        put(PropertyKeys.TEXT_COLOR, j.optString("color").ifEmpty { null })
        put(PropertyKeys.BG_COLOR, j.optString("bg").ifEmpty { null })
        put(PropertyKeys.CORNER_RADIUS, num("radius"))
        num("padding")?.let { p ->
            listOf(PropertyKeys.PADDING_START, PropertyKeys.PADDING_TOP, PropertyKeys.PADDING_END, PropertyKeys.PADDING_BOTTOM).forEach { put(it, p) }
        }
        put(PropertyKeys.SPACING, num("spacing"))
        put(PropertyKeys.WEIGHT, num("weight"))
        val align = j.optString("align").lowercase()
        if (align in setOf(Aligns.START, Aligns.CENTER, Aligns.END)) {
            if (type == ElementTypes.TEXT || type == ElementTypes.BUTTON) put(PropertyKeys.TEXT_ALIGN, align)
            else put(PropertyKeys.CHILD_ALIGN_H, align)
        }
        j.optJSONObject("props")?.let { extra -> extra.keys().forEach { k -> put(k, extra.optString(k)) } }

        val (dw, dh) = ElementTypes.defaultSize(type)
        val fillByDefault = type in setOf(ElementTypes.INPUT, ElementTypes.DIVIDER, ElementTypes.CARD, ElementTypes.CONTAINER, ElementTypes.LIST)
        val width = size(j.opt("width"), if (fillByDefault) SizeSpec(SizeMode.MATCH_PARENT, dw) else SizeSpec.fixed(dw), dw)
        val height = size(j.opt("height"), if (type == ElementTypes.TEXT) SizeSpec(SizeMode.WRAP_CONTENT, dh) else SizeSpec.fixed(dh), dh)

        val hasKids = type == ElementTypes.CONTAINER || type == ElementTypes.CARD
        val kids = if (hasKids) parseList(j.optJSONArray("children"), depth + 1, budget, nav) else emptyList()
        val layout = if (!hasKids) ChildLayout.ABSOLUTE else when (j.optString("layout").lowercase()) {
            "row" -> ChildLayout.ROW
            "box" -> ChildLayout.BOX
            "absolute" -> ChildLayout.ABSOLUTE
            else -> ChildLayout.COLUMN
        }
        // عنصر فيه أبناء بلا ارتفاع صريح يتبع محتواه.
        val finalHeight = if (hasKids && !j.has("height")) SizeSpec(SizeMode.WRAP_CONTENT, dh) else height
        val el = UiElement(
            id = newId(), type = type,
            layout = ElementLayout(0f, 0f, width, finalHeight),
            childLayout = layout, properties = props, children = kids
        )
        val go = j.optString("go").trim()
        return when {
            go.isNotEmpty() -> { nav += el.id to go; el }
            j.optBoolean("back", false) -> Interactions.withClickAction(el, ClickAction.Back)
            else -> el
        }
    }

    private fun size(raw: Any?, default: SizeSpec, fallbackValue: Float): SizeSpec = when {
        raw == null || raw == JSONObject.NULL -> default
        raw is Number -> SizeSpec.fixed(raw.toFloat().coerceIn(1f, 2000f))
        else -> when (val s = raw.toString().trim().lowercase()) {
            "fill", "match", "match_parent" -> SizeSpec(SizeMode.MATCH_PARENT, fallbackValue)
            "wrap", "wrap_content" -> SizeSpec(SizeMode.WRAP_CONTENT, fallbackValue)
            else -> s.toFloatOrNull()?.let { SizeSpec.fixed(it.coerceIn(1f, 2000f)) } ?: default
        }
    }
}
