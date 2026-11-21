package com.arabicuieditor.data.ai

import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.withProp

/**
 * «تعبئة المحتوى بالذكاء الاصطناعي»: الشكل جاهز (من المخطِّط أو القوالب) والذكاء الاصطناعي يبدّل النصوص فقط.
 * نص الطلب قصير لأنه يضم النصوص الحالية مرقّمة، والرد JSON صغير {"texts":{"1":"..."}}.
 */
object AiContent {
    const val MAX_SLOTS = 120
    private const val MAX_TEXT = 200

    /** نصوص المشروع القابلة للتبديل بالترتيب: (معرّف العنصر، اسم الشاشة، النص الحالي). */
    data class Slot(val elementId: String, val screen: String, val text: String)

    fun slots(project: Project): List<Slot> = buildList {
        for (s in project.screens) {
            for (e in ElementTree.flatten(s.elements)) {
                if (size >= MAX_SLOTS) return@buildList
                val t = e.properties[PropertyKeys.TEXT].orEmpty()
                if ((e.type == ElementTypes.TEXT || e.type == ElementTypes.BUTTON) && t.isNotBlank()) add(Slot(e.id, s.name, t))
            }
        }
    }

    fun prompt(project: Project, idea: String): String = buildString {
        appendLine("أنا أصمّم تطبيقًا بالعربية، والشكل جاهز. المطلوب منك فقط كتابة محتوى النصوص بما يناسب فكرة التطبيق.")
        appendLine()
        append("فكرة التطبيق: ").appendLine(idea.trim().ifEmpty { project.name })
        appendLine()
        appendLine("النصوص الحالية مرقّمة وفق الشاشات:")
        var lastScreen = ""
        slots(project).forEachIndexed { i, s ->
            if (s.screen != lastScreen) { appendLine("[شاشة: ${s.screen}]"); lastScreen = s.screen }
            appendLine("${i + 1}: ${s.text.replace('\n', ' ').take(MAX_TEXT)}")
        }
        appendLine()
        appendLine("أعطني الرد JSON واحدًا فقط بلا شرح ولا علامات ``` بهذه الصيغة:")
        appendLine("""{"texts":{"1":"النص الجديد","2":"النص الجديد"}}""")
        appendLine("قواعد: احتفظ بنفس الأرقام، واجعل كل نص قريبًا من طول الأصلي، واكتب بالعربية الفصحى المبسطة بمحتوى واقعي (أسماء وأسعار وأوصاف حقيقية الشكل)، وأبقِ أسماء الأزرار قصيرة.")
    }

    /** يطبّق الرد: يعيد المشروع الجديد وعدد النصوص المبدَّلة، أو null إن لم يحوِ الرد نصوصًا صالحة. */
    fun apply(project: Project, reply: String): Pair<Project, Int>? {
        val texts = AiProject.extractJson(reply)?.optJSONObject("texts") ?: return null
        val slots = slots(project)
        val changes = LinkedHashMap<String, String>()
        for ((i, slot) in slots.withIndex()) {
            val v = texts.optString((i + 1).toString(), "").trim()
            if (v.isNotEmpty() && v != slot.text) changes[slot.elementId] = v.take(MAX_TEXT * 3)
        }
        if (changes.isEmpty()) return null
        val screens = project.screens.map { s ->
            s.copy(elements = changes.entries.fold(s.elements) { acc, (id, text) ->
                ElementTree.update(acc, id) { e: UiElement -> e.withProp(PropertyKeys.TEXT, text) }
            })
        }
        return project.copy(screens = screens) to changes.size
    }
}
