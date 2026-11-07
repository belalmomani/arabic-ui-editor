package com.arabicuieditor.data.model

import com.arabicuieditor.R

/** حركة ظهور الشاشة عند الانتقال إليها (في المعاينة والتصدير). خاصية على الشاشة نفسها. */
object ScreenTransitions {
    const val NONE = "none"
    const val FADE = "fade"
    const val SLIDE = "slide"
    const val SCALE = "scale"

    val options: List<Pair<String, Int>> = listOf(
        NONE to R.string.trans_none,
        FADE to R.string.trans_fade,
        SLIDE to R.string.trans_slide,
        SCALE to R.string.trans_scale
    )

    fun isAnimated(kind: String) = kind == FADE || kind == SLIDE || kind == SCALE

    /** تعبير Compose لحركة الدخول (للكود المُصدَّر). */
    fun enterExpr(kind: String): String? = when (kind) {
        FADE -> "fadeIn()"
        SLIDE -> "slideInHorizontally { it / 3 } + fadeIn()"
        SCALE -> "scaleIn(initialScale = 0.9f) + fadeIn()"
        else -> null
    }
}
