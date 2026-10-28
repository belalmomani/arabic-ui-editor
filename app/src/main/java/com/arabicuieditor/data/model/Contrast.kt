package com.arabicuieditor.data.model

import kotlin.math.pow

/** فحص تباين الألوان وفق WCAG 2.x (دوال نقية بلا اعتماد على الواجهة). */
object Contrast {
    /** الحد الأدنى المطلوب: AA للنص العادي 4.5، وللنص الكبير 3، وAAA للعادي 7. */
    const val AA_NORMAL = 4.5
    const val AA_LARGE = 3.0
    const val AAA_NORMAL = 7.0

    enum class Level { FAIL, AA_LARGE_ONLY, AA, AAA }

    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    /** الإضاءة النسبية لـ ARGB (يتجاهل الشفافية). */
    fun luminance(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    /** يمزج [fg] نصف الشفاف فوق [bg] ويعيد لونًا معتمًا. */
    fun over(fg: Int, bg: Int): Int {
        val a = (fg ushr 24) / 255.0
        fun mix(shift: Int): Int {
            val f = (fg shr shift) and 0xFF
            val b = (bg shr shift) and 0xFF
            return (f * a + b * (1 - a)).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** نسبة التباين من 1 إلى 21 بين النص [fg] والخلفية [bg] (الشفاف يُمزج فوق [bg]). */
    fun ratio(fg: Int, bg: Int): Double {
        val opaqueBg = over(bg, 0xFFFFFFFF.toInt())
        val opaqueFg = over(fg, opaqueBg)
        val l1 = luminance(opaqueFg)
        val l2 = luminance(opaqueBg)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    fun level(ratio: Double): Level = when {
        ratio >= AAA_NORMAL -> Level.AAA
        ratio >= AA_NORMAL -> Level.AA
        ratio >= AA_LARGE -> Level.AA_LARGE_ONLY
        else -> Level.FAIL
    }

    /** النص «كبير» عند 18sp فأكثر (WCAG يعدّ 14pt عريضًا ≈ 18.66px؛ نبسّطها بـ 18). */
    fun isLargeText(fontSize: Int): Boolean = fontSize >= 18
}
