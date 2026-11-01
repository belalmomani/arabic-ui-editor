package com.arabicuieditor.data.model

/**
 * فهرس الخطوط. القيمة المخزنة في خاصية `fontFamily` معرّف نصي:
 * - أحد خطوط النظام: default / sans / serif / mono / cursive (تعمل دائمًا).
 * - أو اسم ملف خط مخصص موجود في `app/src/main/res/font/` (مثل `cairo` لملف cairo.ttf).
 *   التطبيق يكتشف الملفات الموجودة وقت التشغيل؛ غير الموجود يرجع إلى الافتراضي.
 */
object FontCatalog {
    const val DEFAULT = "default"
    const val SANS = "sans"
    const val SERIF = "serif"
    const val MONO = "mono"
    const val CURSIVE = "cursive"

    val system = listOf(DEFAULT, SANS, SERIF, MONO, CURSIVE)

    /** خط مقترح: [id] هو اسم الملف بلا امتداد (أحرف صغيرة وأرقام و_ فقط)، و[label] اسمه المعروض. */
    class Candidate(val id: String, val label: String)

    /** خطوط عربية مفتوحة المصدر (OFL) من Google Fonts، تظهر في القائمة إذا وُضع ملفها في res/font. */
    val candidates = listOf(
        Candidate("cairo", "Cairo — القاهرة"),
        Candidate("tajawal", "Tajawal — تجوّل"),
        Candidate("almarai", "Almarai — المراعي"),
        Candidate("amiri", "Amiri — أميري"),
        Candidate("noto_kufi_arabic", "Noto Kufi Arabic — كوفي"),
        Candidate("noto_naskh_arabic", "Noto Naskh Arabic — نسخ"),
        Candidate("ibm_plex_sans_arabic", "IBM Plex Sans Arabic"),
        Candidate("readex_pro", "Readex Pro"),
        Candidate("changa", "Changa — شانغا"),
        Candidate("el_messiri", "El Messiri — المسيري"),
        Candidate("reem_kufi", "Reem Kufi — ريم كوفي"),
        Candidate("lemonada", "Lemonada — ليمونادا"),
        Candidate("lateef", "Lateef — لطيف"),
        Candidate("scheherazade_new", "Scheherazade New — شهرزاد"),
        Candidate("markazi_text", "Markazi Text — مركزي"),
        Candidate("mada", "Mada — مدى"),
        Candidate("harmattan", "Harmattan — هرمتان"),
        Candidate("rakkas", "Rakkas — رقاص")
    )

    private val ID_REGEX = Regex("^[a-z][a-z0-9_]{0,39}$")

    fun isSystem(id: String) = id in system

    /** معرّف صالح للتخزين: من خطوط النظام أو اسم مورد صالح لملف خط. */
    fun isValid(id: String) = isSystem(id) || ID_REGEX.matches(id)
}
