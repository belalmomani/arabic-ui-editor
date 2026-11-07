package com.arabicuieditor.data.model

import com.arabicuieditor.R

/**
 * أنماط الأزرار الفنية. وصف واحد يقرؤه المحرر (ElementView) والمُصدِّر (ComposeExporter)
 * فيتطابق المظهر في الاثنين. اللون الأساسي هو خلفية الزر.
 */
object ButtonStyles {
    const val FLAT = "flat"
    const val GRADIENT = "gradient"
    const val GLASS = "glass"
    const val RAISED_3D = "raised3d"
    const val METALLIC = "metallic"
    const val NEON = "neon"
    const val GLOW = "glow"
    const val SOFT = "soft"

    val options: List<Pair<String, Int>> = listOf(
        FLAT to R.string.bstyle_flat,
        GRADIENT to R.string.bstyle_gradient,
        GLASS to R.string.bstyle_glass,
        RAISED_3D to R.string.bstyle_3d,
        METALLIC to R.string.bstyle_metallic,
        NEON to R.string.bstyle_neon,
        GLOW to R.string.bstyle_glow,
        SOFT to R.string.bstyle_soft
    )

    /** لون متدرج: mix موجب = نحو الأبيض، سالب = نحو الأسود (من اللون الأساسي). */
    class Stop(val mix: Float, val alpha: Float = 1f)

    class Look(
        val stops: List<Stop>,
        val elevationDp: Int,
        /** الظل بلون الزر الأساسي (توهج) بدل الأسود. */
        val glow: Boolean = false,
        /** تعبئة داكنة ثابتة بدل التدرج (النيون). */
        val darkFill: Boolean = false,
        val borderDp: Int = 0,
        /** الحد بلون الزر الأساسي، وإلا أبيض شبه شفاف. */
        val borderBase: Boolean = false
    )

    const val DARK_FILL_ARGB = 0xFF0B1220.toInt()
    const val GLASS_BORDER_ALPHA = 0.6f

    /** null = الزر المسطح (يُرسم بالطريقة العادية). */
    fun look(style: String): Look? = when (style) {
        GRADIENT -> Look(listOf(Stop(0.25f), Stop(-0.15f)), 0)
        SOFT -> Look(listOf(Stop(0.12f), Stop(-0.05f)), 8)
        RAISED_3D -> Look(listOf(Stop(0.3f), Stop(-0.25f)), 6)
        METALLIC -> Look(listOf(Stop(0.6f), Stop(0.1f), Stop(-0.2f), Stop(0.35f)), 3)
        GLASS -> Look(listOf(Stop(0.8f, 0.5f), Stop(0.1f, 0.3f)), 0, borderDp = 1)
        GLOW -> Look(listOf(Stop(0.2f), Stop(-0.1f)), 14, glow = true)
        NEON -> Look(emptyList(), 12, glow = true, darkFill = true, borderDp = 2, borderBase = true)
        else -> null
    }
}

/** تأثير الضغط على الزر (في المعاينة والتصدير): تصغير، تعتيم، أو تكبير خفيف أثناء اللمس. */
object PressEffects {
    const val NONE = "none"
    const val SHRINK = "shrink"
    const val DIM = "dim"
    const val GROW = "grow"

    val options: List<Pair<String, Int>> = listOf(
        NONE to R.string.press_none,
        SHRINK to R.string.press_shrink,
        DIM to R.string.press_dim,
        GROW to R.string.press_grow
    )

    fun scaleOf(kind: String): Float = when (kind) { SHRINK -> 0.94f; GROW -> 1.06f; else -> 1f }
    fun alphaOf(kind: String): Float = if (kind == DIM) 0.7f else 1f
}
