package com.arabicuieditor.ui.editor.canvas

import kotlin.math.max
import kotlin.math.min

/** حسابات نقية للمصغّرات: حدود المحتوى الفعلي ومقياس ملاءمته داخل مساحة العرض. */
object ThumbnailMath {
    /** أكبر تكبير مسموح (المكوّنات الصغيرة لا تُضخَّم أكثر من هذا). */
    const val MAX_SCALE = 1.5f

    /** المستطيل المحيط بإطارات [ids]؛ فارغ أو بلا إطارات يعيد null. */
    fun bounds(layout: LayoutResult, ids: Collection<String>): DesignRect? {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        var any = false
        for (id in ids) {
            val f = layout.frames[id] ?: continue
            any = true
            left = min(left, f.x); top = min(top, f.y)
            right = max(right, f.x + f.w); bottom = max(bottom, f.y + f.h)
        }
        return if (any) DesignRect(left, top, max(1f, right - left), max(1f, bottom - top)) else null
    }

    /** مقياس يُدخل [crop] كاملًا في ([w] × [h]) دون تجاوز [MAX_SCALE]. */
    fun scaleFor(crop: DesignRect, w: Float, h: Float): Float {
        if (crop.w <= 0f || crop.h <= 0f || w <= 0f || h <= 0f) return 1f
        return min(min(w / crop.w, h / crop.h), MAX_SCALE)
    }
}
