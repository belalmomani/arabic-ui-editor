package com.arabicuieditor.data.model

import kotlin.math.max

/** الحواف والمراكز التي يمكن ربطها في حاوية بتخطيط «قيود». */
enum class AnchorEdge(val key: String) {
    LEFT("anchorL"), RIGHT("anchorR"), TOP("anchorT"), BOTTOM("anchorB"),
    CENTER_H("anchorCH"), CENTER_V("anchorCV")
}

/**
 * ربط حافة: [target] إما [Constraints.PARENT] أو معرّف أخ في الحاوية نفسها، و[margin] المسافة بالـdp.
 * يُخزَّن نصًا "<target>:<margin>" في خصائص العنصر (بلا ترقية للمخطط).
 */
data class Anchor(val target: String, val margin: Int)

/**
 * قيود العناصر داخل حاوية تخطيطها CONSTRAINT.
 *
 * - اليسار/الأعلى مربوط بأخ: يبدأ بعد حافته اليمنى/السفلى بمسافة؛ اليمين/الأسفل: ينتهي قبل حافته اليسرى/العلوية.
 * - مربوط بالأب: يبعد عن حافة الأب بمسافة.
 * - ربط الجانبين معًا يمدّد العنصر بينهما (فيتكيف مع أي حجم شاشة).
 * - المركز (أفقي/رأسي) يوسّط العنصر في الأب.
 * - محور بلا أي ربط يبقى بإحداثيات (x, y) الحرة.
 * - هدف محذوف أو دائرة ربط تُعامل كأن الربط غير موجود.
 */
object Constraints {
    const val PARENT = "parent"

    fun read(e: UiElement, edge: AnchorEdge): Anchor? {
        val raw = e.properties[edge.key] ?: return null
        val i = raw.lastIndexOf(':')
        if (i <= 0) return null
        val margin = raw.substring(i + 1).toIntOrNull() ?: return null
        return Anchor(raw.substring(0, i), margin.coerceIn(0, MAX_MARGIN))
    }

    fun write(e: UiElement, edge: AnchorEdge, anchor: Anchor?): UiElement =
        if (anchor == null) e.copy(properties = e.properties - edge.key)
        else e.copy(properties = e.properties + (edge.key to "${anchor.target}:${anchor.margin.coerceIn(0, MAX_MARGIN)}"))

    fun hasAny(e: UiElement): Boolean = AnchorEdge.entries.any { read(e, it) != null }

    /** يبدّل أهداف الربط حسب [idMap] (عند نسخ شجرة بمعرّفات جديدة). */
    fun remap(e: UiElement, idMap: Map<String, String>): UiElement {
        var out = e
        for (edge in AnchorEdge.entries) {
            val a = read(e, edge) ?: continue
            val mapped = idMap[a.target] ?: continue
            out = write(out, edge, a.copy(target = mapped))
        }
        return out
    }

    /**
     * يحلّ محورًا واحدًا. المدخلات إحداثيات مطلقة أو null: [start] حافة البداية، [end] حافة النهاية،
     * [center] مركز الحاوية، و[fallback] الموضع الحر. يعيد (الموضع، الحجم).
     */
    fun solveAxis(start: Float?, end: Float?, center: Float?, size: Float, fallback: Float, min: Float): Pair<Float, Float> = when {
        start != null && end != null -> start to max(min, end - start)
        start != null -> start to size
        end != null -> (end - size) to size
        center != null -> (center - size / 2f) to size
        else -> fallback to size
    }

    const val MAX_MARGIN = 1000
}
