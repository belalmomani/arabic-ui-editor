package com.arabicuieditor.data.planner

import com.arabicuieditor.R
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen

/**
 * خانات المحتوى القابلة للتخصيص بعد الإنشاء التلقائي: نصوص وصور (وبريد/هاتف إن وُجدت في القالب).
 * لا تُنشئ عناصر ولا تغيّر التخطيط: تقرأ ما هو موجود فقط وتبدّل النص أو الصورة. القيمة الفارغة لا تغيّر شيئًا.
 */
enum class SlotKind { TEXT, IMAGE, EMAIL, PHONE }

data class ContentSlot(
    val elementId: String,
    val screenId: String,
    val screenName: String,
    val kind: SlotKind,
    /** النص الحالي (أو فارغ للصورة). */
    val current: String
)

object ContentSlots {
    /** مفتاح خاصية اختيارية تُكتب عند الإنشاء لتمييز عناصر التواصل (مفتاح حر يحفظه JSON كما هو). */
    const val SLOT_KEY = "slot"
    const val SLOT_EMAIL = "email"
    const val SLOT_PHONE = "phone"
    const val MAX_SLOTS = 150

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val PHONE = Regex("^\\+?[0-9٠-٩][0-9٠-٩\\s-]{5,18}$")

    fun isValid(kind: SlotKind, value: String): Boolean {
        val v = value.trim()
        return when (kind) {
            SlotKind.EMAIL -> EMAIL.matches(v)
            SlotKind.PHONE -> PHONE.matches(v)
            SlotKind.TEXT -> v.isNotEmpty()
            SlotKind.IMAGE -> com.arabicuieditor.data.model.ImageNames.isValid(v)
        }
    }

    /** يوسم عناصر التواصل في المشروع المولَّد (البريد النموذجي في شاشة التواصل) لتمييزها لاحقًا. */
    fun tagPlanned(screens: List<UiScreen>, text: (Int) -> String): List<UiScreen> {
        val sampleEmail = text(R.string.pc_profile_email)
        return screens.map { s ->
            var elements = s.elements
            ElementTree.flatten(s.elements)
                .filter { it.type == ElementTypes.TEXT && it.properties[PropertyKeys.TEXT] == sampleEmail }
                .forEach { e ->
                    elements = ElementTree.update(elements, e.id) { it.copy(properties = it.properties + (SLOT_KEY to SLOT_EMAIL)) }
                }
            s.copy(elements = elements)
        }
    }

    /** الخانات الموجودة فعلًا بالترتيب (شاشة بعد شاشة). المشاريع القديمة بلا وسم تعطي قائمة عامة نصًا وصورًا. */
    fun extract(project: Project): List<ContentSlot> = buildList {
        for (s in project.screens) {
            for (e in ElementTree.flatten(s.elements)) {
                if (size >= MAX_SLOTS) return@buildList
                val kind = kindOf(e) ?: continue
                add(ContentSlot(e.id, s.id, s.name, kind, if (kind == SlotKind.IMAGE) "" else e.properties[PropertyKeys.TEXT].orEmpty()))
            }
        }
    }

    private fun kindOf(e: UiElement): SlotKind? = when {
        e.type == ElementTypes.IMAGE -> SlotKind.IMAGE
        e.type == ElementTypes.TEXT && e.properties[PropertyKeys.TEXT].orEmpty().isNotBlank() ->
            when (e.properties[SLOT_KEY]) {
                SLOT_EMAIL -> SlotKind.EMAIL
                SLOT_PHONE -> SlotKind.PHONE
                else -> SlotKind.TEXT
            }
        else -> null
    }

    /**
     * يطبّق قيمًا (معرّف العنصر ← قيمة). الفارغ أو غير الصالح يُتجاهل فيبقى العنصر كما هو.
     * الصورة: اسم ملف صالح؛ تُزال أيقونة المكان النائب ليظهر الملف. لا يتغير أي معرّف أو تخطيط أو حدث.
     */
    fun apply(project: Project, values: Map<String, String>): Project {
        if (values.isEmpty()) return project
        val kinds = extract(project).associate { it.elementId to it.kind }
        var changed = false
        val screens = project.screens.map { s ->
            var elements = s.elements
            for ((id, raw) in values) {
                val kind = kinds[id] ?: continue
                val v = raw.trim()
                if (v.isEmpty() || !isValid(kind, v)) continue
                if (ElementTree.flatten(elements).none { it.id == id }) continue
                elements = ElementTree.update(elements, id) { e ->
                    if (kind == SlotKind.IMAGE) e.copy(properties = e.properties - PropertyKeys.ICON + (PropertyKeys.IMAGE_SRC to v))
                    else e.copy(properties = e.properties + (PropertyKeys.TEXT to v.take(200)))
                }
                changed = true
            }
            s.copy(elements = elements)
        }
        return if (changed) project.copy(screens = screens) else project
    }
}
