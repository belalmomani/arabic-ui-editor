package com.arabicuieditor.data.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * يشتق نسخة «ليلية» من نظام التصميم لمعاينتها فقط (لا تُخزَّن ولا تغيّر المشروع).
 * الألوان المحايدة (خلفية، سطح، نص، خافت، حد) يُعاد ضبط إضاءتها مع الحفاظ على صبغتها،
 * أما الأساسي والخطر فيُفتَّحان عند الحاجة لتبقى نسبة تباينهما مع الخلفية ≥ 3.
 * إن كان المظهر ليليًا أصلًا (خلفيته معتمة) تُعاد النسخة كما هي.
 */
object DarkMode {
    private const val BACKGROUND_L = 0.07f
    private const val SURFACE_L = 0.13f
    private const val BORDER_L = 0.26f
    private const val TEXT_L = 0.92f
    private const val MUTED_L = 0.68f
    private const val MIN_ACCENT_CONTRAST = 3.0

    fun isDark(ds: DesignSystem): Boolean = Contrast.luminance(ds.color(Tokens.BACKGROUND)) < 0.18

    fun apply(ds: DesignSystem): DesignSystem {
        if (isDark(ds)) return ds
        val bg = withLightness(ds.color(Tokens.BACKGROUND), BACKGROUND_L)
        var out = ds
            .withColor(Tokens.BACKGROUND, bg)
            .withColor(Tokens.SURFACE, withLightness(ds.color(Tokens.SURFACE), SURFACE_L))
            .withColor(Tokens.BORDER, withLightness(ds.color(Tokens.BORDER), BORDER_L))
            .withColor(Tokens.TEXT_COLOR, withLightness(ds.color(Tokens.TEXT_COLOR), TEXT_L))
            .withColor(Tokens.MUTED, withLightness(ds.color(Tokens.MUTED), MUTED_L))
        for (name in listOf(Tokens.PRIMARY, Tokens.DANGER)) {
            out = out.withColor(name, liftUntilReadable(ds.color(name), bg))
        }
        return out
    }

    private fun liftUntilReadable(color: Int, background: Int): Int {
        var c = color
        var guard = 0
        while (Contrast.ratio(c, background) < MIN_ACCENT_CONTRAST && guard++ < 20) {
            val hsl = toHsl(c)
            c = fromHsl(hsl[0], hsl[1], min(1f, hsl[2] + 0.05f), c ushr 24)
        }
        return c
    }

    private fun withLightness(color: Int, l: Float): Int {
        val hsl = toHsl(color)
        // نخفّف التشبّع قليلًا كي لا تبدو الأسطح الداكنة ملوّنة بإفراط.
        return fromHsl(hsl[0], min(hsl[1], 0.35f), l, color ushr 24)
    }

    internal fun toHsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        if (d == 0f) return floatArrayOf(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        val h = when (mx) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return floatArrayOf(if (h < 0) h + 360f else h, s.coerceIn(0f, 1f), l)
    }

    internal fun fromHsl(h: Float, s: Float, l: Float, alpha: Int): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
