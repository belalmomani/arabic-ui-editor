package com.arabicuieditor.data.model

import com.arabicuieditor.R
import com.arabicuieditor.data.script.Steps

/**
 * أصوات قصيرة مضمّنة (WAV أحادي 22kHz، من توليد المشروع نفسه) تُختار لعنصر عند النقر، أو لشاشة عند ظهورها،
 * أو كخطوة منطق. الملفات في assets/sounds باسم snd_<معرّف>.wav؛ وفي المشروع المُصدَّر res/raw/snd_<معرّف>.wav.
 */
object Sounds {
    const val NONE = "none"
    const val ASSET_DIR = "sounds"
    const val PREFIX = "snd_"

    /** (المعرّف، النص العربي) بالترتيب الذي تُعرض به. */
    val all: List<Pair<String, Int>> = listOf(
        "click" to R.string.snd_click,
        "tap" to R.string.snd_tap,
        "pop" to R.string.snd_pop,
        "switch" to R.string.snd_switch,
        "swipe" to R.string.snd_swipe,
        "whoosh" to R.string.snd_whoosh,
        "ding" to R.string.snd_ding,
        "bell" to R.string.snd_bell,
        "notify" to R.string.snd_notify,
        "coin" to R.string.snd_coin,
        "success" to R.string.snd_success,
        "error" to R.string.snd_error,
        "tada" to R.string.snd_tada
    )

    /** خيارات الخاصية: «بدون» أولًا. */
    val options: List<Pair<String, Int>> = listOf(NONE to R.string.snd_none) + all

    private val ids = all.map { it.first }.toSet()

    /** صوت مستورد من جهاز المستخدم: usr_<رقم>.wav|mp3|ogg|m4a داخل مجلد موارد المشروع. */
    const val CUSTOM_PREFIX = "usr_"
    const val MAX_CUSTOM_BYTES = 1_500_000
    private val CUSTOM = Regex("^usr_[0-9]{1,4}\\.(wav|mp3|ogg|m4a)$")

    fun isCustom(id: String) = CUSTOM.matches(id)
    fun isValid(id: String) = id in ids || isCustom(id)
    fun fileName(id: String) = if (isCustom(id)) id else "$PREFIX$id.wav"
    /** مسار الصوت المضمّن في assets (للمضمّن فقط). */
    fun assetPath(id: String) = "$ASSET_DIR/${fileName(id)}"
    /** اسم المورد في res/raw (بلا امتداد). */
    fun rawName(id: String) = if (isCustom(id)) id.substringBeforeLast('.') else "$PREFIX$id"

    /** امتداد الملف المناسب من نوع MIME أو من اسم الملف؛ null إن لم يكن صيغة مدعومة. */
    fun customExt(mime: String?, displayName: String?): String? {
        when (mime?.lowercase()) {
            "audio/mpeg", "audio/mp3" -> return "mp3"
            "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> return "wav"
            "audio/ogg", "application/ogg" -> return "ogg"
            "audio/mp4", "audio/x-m4a", "audio/aac", "audio/m4a" -> return "m4a"
        }
        val ext = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return ext.takeIf { it in setOf("wav", "mp3", "ogg", "m4a") }
    }

    /** اسم جديد غير مستعمل (بأي امتداد): usr_1.mp3، usr_2.wav... */
    fun nextCustomName(existing: Set<String>, ext: String): String {
        val bases = existing.map { it.substringBeforeLast('.') }.toSet()
        var i = 1
        while ("$CUSTOM_PREFIX$i" in bases) i++
        return "$CUSTOM_PREFIX$i.$ext"
    }

    /** الأصوات المستعملة فعلًا في المشروع (عناصر وشاشات وخطوات منطق) بلا تكرار وبترتيب الظهور. */
    /** المعرّف نفسه إن كان صوتًا صالحًا، وإلا null (يشمل «بدون»). */
    fun valid(id: String): String? = id.takeIf { isValid(it) }

    fun usedIn(project: Project): List<String> {
        val out = LinkedHashSet<String>()
        for (s in project.screens) {
            s.prop(PropertyKeys.ENTER_SOUND).takeIf { isValid(it) }?.let { out += it }
            for (e in ElementTree.flatten(s.elements)) {
                e.prop(PropertyKeys.SOUND).takeIf { isValid(it) }?.let { out += it }
                e.prop(PropertyKeys.LONG_SOUND).takeIf { isValid(it) }?.let { out += it }
                for (trigger in listOf(Interactions.TRIGGER_CLICK, Interactions.TRIGGER_LONG_PRESS)) {
                    (Interactions.actionFor(e, trigger) as? ClickAction.Blocks)?.steps.orEmpty()
                        .filter { it.kind == Steps.SOUND && isValid(it.a) }
                        .forEach { out += it.a }
                }
            }
        }
        return out.toList()
    }
}
