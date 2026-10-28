package com.arabicuieditor.data.model

import androidx.annotation.StringRes
import com.arabicuieditor.R

/**
 * نظام التصميم: قيم مركزية للألوان وأنصاف الأقطار (الزوايا) وأحجام النصوص.
 * العناصر لا تحفظ القيمة بل «مرجعًا» إليها (مثل `@color/primary`)، فإذا تغيّرت قيمة في النظام
 * تغيّرت كل العناصر المرتبطة بها. يمكن كذلك كتابة قيمة مباشرة (hex أو رقم) فتبقى مستقلة.
 *
 * صيغة المرجع: `@color/<اسم>` أو `@radius/<اسم>` أو `@text/<اسم>`.
 */
data class DesignSystem(
    val colors: Map<String, Int> = DEFAULT_COLORS,
    val radii: Map<String, Int> = DEFAULT_RADII,
    val textSizes: Map<String, Int> = DEFAULT_TEXT_SIZES
) {
    fun color(name: String): Int = colors[name] ?: DEFAULT_COLORS[name] ?: 0xFF000000.toInt()
    fun radius(name: String): Int = radii[name] ?: DEFAULT_RADII[name] ?: 0
    fun textSize(name: String): Int = textSizes[name] ?: DEFAULT_TEXT_SIZES[name] ?: 14

    fun withColor(name: String, argb: Int) = copy(colors = colors + (name to argb))
    fun withRadius(name: String, dp: Int) = copy(radii = radii + (name to dp.coerceIn(0, 100)))
    fun withTextSize(name: String, sp: Int) = copy(textSizes = textSizes + (name to sp.coerceIn(8, 72)))

    /** يملأ أي قيمة ناقصة بالافتراضية (مشاريع قديمة أو ملفات ناقصة). */
    fun completed() = DesignSystem(DEFAULT_COLORS + colors, DEFAULT_RADII + radii, DEFAULT_TEXT_SIZES + textSizes)

    companion object {
        val DEFAULT = DesignSystem()
    }
}

/** أسماء الرموز (tokens) ومراجعها. */
object Tokens {
    const val COLOR = "@color/"
    const val RADIUS = "@radius/"
    const val TEXT = "@text/"

    // ألوان
    const val PRIMARY = "primary"
    const val ON_PRIMARY = "onPrimary"
    const val BACKGROUND = "background"
    const val SURFACE = "surface"
    const val TEXT_COLOR = "text"
    const val MUTED = "muted"
    const val BORDER = "border"
    const val DANGER = "danger"

    // زوايا
    const val SMALL = "small"
    const val MEDIUM = "medium"
    const val LARGE = "large"

    // نصوص
    const val CAPTION = "caption"
    const val BODY = "body"
    const val TITLE = "title"
    const val HEADING = "heading"

    fun color(name: String) = COLOR + name
    fun radius(name: String) = RADIUS + name
    fun text(name: String) = TEXT + name

    val colorNames = listOf(PRIMARY, ON_PRIMARY, BACKGROUND, SURFACE, TEXT_COLOR, MUTED, BORDER, DANGER)
    val radiusNames = listOf(SMALL, MEDIUM, LARGE)
    val textNames = listOf(CAPTION, BODY, TITLE, HEADING)

    fun isToken(value: String) = value.startsWith("@")

    @StringRes
    fun colorLabel(name: String): Int = when (name) {
        PRIMARY -> R.string.tok_primary
        ON_PRIMARY -> R.string.tok_on_primary
        BACKGROUND -> R.string.tok_background
        SURFACE -> R.string.tok_surface
        TEXT_COLOR -> R.string.tok_text
        MUTED -> R.string.tok_muted
        BORDER -> R.string.tok_border
        else -> R.string.tok_danger
    }

    @StringRes
    fun radiusLabel(name: String): Int = when (name) {
        SMALL -> R.string.tok_small
        MEDIUM -> R.string.tok_medium
        else -> R.string.tok_large
    }

    @StringRes
    fun textLabel(name: String): Int = when (name) {
        CAPTION -> R.string.tok_caption
        BODY -> R.string.tok_body
        TITLE -> R.string.tok_title
        else -> R.string.tok_heading
    }

    /** هل [ref] مرجع صالح للون؟ */
    fun isColorRef(ref: String) = ref.startsWith(COLOR) && ref.removePrefix(COLOR) in colorNames
    fun isRadiusRef(ref: String) = ref.startsWith(RADIUS) && ref.removePrefix(RADIUS) in radiusNames
    fun isTextRef(ref: String) = ref.startsWith(TEXT) && ref.removePrefix(TEXT) in textNames

    /** يحلّ مرجع لون إلى ARGB، أو null إن لم يكن مرجعًا صالحًا. */
    fun resolveColor(ref: String, ds: DesignSystem = DesignSystem.DEFAULT): Int? =
        if (isColorRef(ref)) ds.color(ref.removePrefix(COLOR)) else null

    /** يحلّ مرجع رقمي (زاوية أو حجم نص) إلى قيمته، أو null. */
    fun resolveInt(ref: String, ds: DesignSystem = DesignSystem.DEFAULT): Int? = when {
        isRadiusRef(ref) -> ds.radius(ref.removePrefix(RADIUS))
        isTextRef(ref) -> ds.textSize(ref.removePrefix(TEXT))
        else -> null
    }

    /** أي خاصية رقمية تقبل مراجع، ومن أي عائلة. */
    fun intFamilyOf(key: String): String? = when (key) {
        PropertyKeys.CORNER_RADIUS -> RADIUS
        PropertyKeys.FONT_SIZE -> TEXT
        else -> null
    }

    /** هل يقبل مفتاح الخاصية هذا المرجع؟ */
    fun accepts(key: String, kind: PropertyKind, ref: String): Boolean = when (kind) {
        PropertyKind.ColorValue -> isColorRef(ref)
        is PropertyKind.Integer -> when (intFamilyOf(key)) {
            RADIUS -> isRadiusRef(ref)
            TEXT -> isTextRef(ref)
            else -> false
        }
        else -> false
    }
}

private fun argb(hex: String): Int = ColorUtil.parse(hex)!!

val DEFAULT_COLORS: Map<String, Int> = mapOf(
    Tokens.PRIMARY to argb("#FF1B5E8C"),
    Tokens.ON_PRIMARY to argb("#FFFFFFFF"),
    Tokens.BACKGROUND to argb("#FFFFFFFF"),
    Tokens.SURFACE to argb("#FFFFFFFF"),
    Tokens.TEXT_COLOR to argb("#FF1C1B1F"),
    Tokens.MUTED to argb("#FF5F6B76"),
    Tokens.BORDER to argb("#FFE0E4EA"),
    Tokens.DANGER to argb("#FFB3261E")
)

val DEFAULT_RADII: Map<String, Int> = mapOf(Tokens.SMALL to 6, Tokens.MEDIUM to 8, Tokens.LARGE to 12)

val DEFAULT_TEXT_SIZES: Map<String, Int> = mapOf(
    Tokens.CAPTION to 12, Tokens.BODY to 14, Tokens.TITLE to 18, Tokens.HEADING to 24
)

/** سمة جاهزة: لوحة ألوان كاملة تُطبَّق دفعة واحدة. */
class DesignTheme(val id: String, @StringRes val labelRes: Int, val colors: Map<String, Int>)

object DesignThemes {
    private fun theme(
        id: String, label: Int,
        primary: String, onPrimary: String = "#FFFFFFFF",
        background: String = "#FFFFFFFF", surface: String = "#FFFFFFFF",
        text: String = "#FF1C1B1F", muted: String = "#FF5F6B76", border: String = "#FFE0E4EA",
        danger: String = "#FFB3261E"
    ) = DesignTheme(
        id, label,
        mapOf(
            Tokens.PRIMARY to argb(primary), Tokens.ON_PRIMARY to argb(onPrimary),
            Tokens.BACKGROUND to argb(background), Tokens.SURFACE to argb(surface),
            Tokens.TEXT_COLOR to argb(text), Tokens.MUTED to argb(muted),
            Tokens.BORDER to argb(border), Tokens.DANGER to argb(danger)
        )
    )

    val all: List<DesignTheme> = listOf(
        theme("ocean", R.string.theme_ocean, "#FF1B5E8C"),
        theme("forest", R.string.theme_forest, "#FF2E7D32", border = "#FFDCE6DC"),
        theme("violet", R.string.theme_violet, "#FF6750A4", border = "#FFE6E0F0"),
        theme("sunset", R.string.theme_sunset, "#FFE8710A", border = "#FFF1E4D6"),
        theme("rose", R.string.theme_rose, "#FFC2185B", border = "#FFF3DCE6"),
        theme(
            "night", R.string.theme_night, "#FF4FC3F7", onPrimary = "#FF00131F",
            background = "#FF121417", surface = "#FF1E2227", text = "#FFECEFF1",
            muted = "#FF9AA5B1", border = "#FF2D343B", danger = "#FFFF8A80"
        )
    )
}

/**
 * نسخة من العنصر تُستبدل فيها كل المراجع (القيم المخزنة والافتراضية) بقيمها الفعلية من [ds].
 * تُستعمل للرسم فقط؛ لا تُخزَّن.
 */
fun UiElement.resolved(ds: DesignSystem): UiElement {
    var out: Map<String, String>? = null
    for (def in PropertyRegistry.forType(type)) {
        val raw = prop(def.key)
        if (!Tokens.isToken(raw)) continue
        val value = when (def.kind) {
            PropertyKind.ColorValue -> Tokens.resolveColor(raw, ds)?.let { ColorUtil.format(it) }
            is PropertyKind.Integer -> Tokens.resolveInt(raw, ds)?.toString()
            else -> null
        } ?: continue
        out = (out ?: properties) + (def.key to value)
    }
    return if (out == null) this else copy(properties = out)
}
