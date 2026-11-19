package com.arabicuieditor.data.model

/** حالة «معطّل» للعنصر: يظهر باهتًا ولا يستجيب للنقر (محرر ومعاينة وتصدير). */
object ElementState {
    /** نسبة الشفافية المطبّقة على العنصر المعطّل فوق شفافيته الأصلية. */
    const val DISABLED_PERCENT = 40

    fun enabled(e: UiElement): Boolean = e.prop(PropertyKeys.ENABLED) != "false"

    /** الشفافية الفعلية بالمئة (0..100) مع أثر التعطيل. */
    fun opacityPercent(base: Int, e: UiElement): Int =
        if (enabled(e)) base else base * DISABLED_PERCENT / 100
}
