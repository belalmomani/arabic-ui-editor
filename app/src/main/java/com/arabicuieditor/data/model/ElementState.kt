package com.arabicuieditor.data.model

/** حالة «معطّل» للعنصر: يظهر باهتًا ولا يستجيب للنقر (محرر ومعاينة وتصدير). */
object ElementState {
    /** نسبة الشفافية المطبّقة على العنصر المعطّل فوق شفافيته الأصلية. */
    const val DISABLED_PERCENT = 40

    fun enabled(e: UiElement): Boolean = e.prop(PropertyKeys.ENABLED) != "false"

    /**
     * العنصر بعد تطبيق ألوان حالة «معطّل» (خلفية ولون نص مخصصان إن ضُبطا؛ الشفاف = غير مضبوط).
     * يُستدعى في كل مرسم ومُصدِّر قبل قراءة الخصائص، فتتوحد النتيجة في المحرر والتصدير.
     */
    fun resolved(e: UiElement): UiElement {
        // المعطّل يغلب «المحدد»؛ كلاهما يبدّل الخلفية ولون النص إن ضُبطا (الشفاف = غير مضبوط).
        val (bgKey, fgKey) = when {
            !enabled(e) -> PropertyKeys.DISABLED_BG to PropertyKeys.DISABLED_TEXT_COLOR
            e.prop(PropertyKeys.SELECTED) == "true" -> PropertyKeys.SELECTED_BG to PropertyKeys.SELECTED_TEXT_COLOR
            else -> return e
        }
        val bg = e.prop(bgKey).takeIf { it != PropertyRegistry.TRANSPARENT && it.isNotBlank() }
        val fg = e.prop(fgKey).takeIf { it != PropertyRegistry.TRANSPARENT && it.isNotBlank() }
        if (bg == null && fg == null) return e
        val p = e.properties.toMutableMap()
        if (bg != null) p[PropertyKeys.BG_COLOR] = bg
        if (fg != null) p[PropertyKeys.TEXT_COLOR] = fg
        return e.copy(properties = p)
    }

    /** لون التظليل عند الضغط (#AARRGGBB) أو null إن لم يُضبط. */
    fun pressTint(e: UiElement): String? =
        e.prop(PropertyKeys.PRESS_TINT).takeIf { it != PropertyRegistry.TRANSPARENT && it.isNotBlank() }

    /** الشفافية الفعلية بالمئة (0..100) مع أثر التعطيل. */
    fun opacityPercent(base: Int, e: UiElement): Int =
        if (enabled(e)) base else base * DISABLED_PERCENT / 100
}
