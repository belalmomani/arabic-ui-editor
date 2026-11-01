package com.arabicuieditor.data.model

/**
 * أسماء ملفات الصور المستوردة داخل مجلد موارد المشروع. منطق نقي قابل للاختبار.
 * الصيغة: img_<رقم>.png|jpg — أحرف لاتينية صغيرة فقط، فتصلح اسمًا لـ drawable في Android.
 */
object ImageNames {
    const val MAX_SIDE_PX = 1600
    private val VALID = Regex("^[a-z][a-z0-9_]{0,39}\\.(png|jpg)$")

    fun isValid(name: String): Boolean = VALID.matches(name)

    /** اسم جديد غير مستعمل: img_1.png، img_2.png... */
    fun nextName(existing: Set<String>, ext: String): String {
        val e = if (ext == "png") "png" else "jpg"
        var i = 1
        while ("img_$i.$e" in existing || "img_$i.${if (e == "png") "jpg" else "png"}" in existing) i++
        return "img_$i.$e"
    }

    /** اسم drawable بلا امتداد. */
    fun drawableName(name: String): String = name.substringBeforeLast('.')

    /** أكبر قوة 2 لـ inSampleSize بحيث لا يتجاوز أطول ضلع [maxSide] بكثير. */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        if (width <= 0 || height <= 0 || maxSide <= 0) return 1
        var s = 1
        while (maxOf(width, height) / (s * 2) >= maxSide) s *= 2
        return s
    }
}
