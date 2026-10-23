package com.arabicuieditor.data.model

import androidx.annotation.StringRes
import com.arabicuieditor.R

/** مجموعات الخصائص في لوحة الخصائص. الترتيب هنا هو ترتيب العرض. */
enum class PropertyGroup(@StringRes val labelRes: Int) {
    GENERAL(R.string.group_general),
    TEXT(R.string.group_text),
    APPEARANCE(R.string.group_appearance),
    SPACING(R.string.group_spacing)
}

/** نوع قيمة الخاصية؛ يحدد أداة التحرير في اللوحة وطريقة التحقق. كل القيم تُخزَّن نصًّا. */
sealed interface PropertyKind {
    data class Text(val multiline: Boolean = false) : PropertyKind

    /** عدد صحيح ضمن [min, max]. */
    data class Integer(val min: Int, val max: Int) : PropertyKind

    /** لون بصيغة #RRGGBB أو #AARRGGBB. */
    data object ColorValue : PropertyKind

    /** اختيار من قيم محددة: (القيمة المخزنة، نص العرض). */
    data class Choice(val options: List<Pair<String, Int>>) : PropertyKind

    /** "true" أو "false". */
    data object Toggle : PropertyKind
}

/** مفاتيح الخصائص المخزنة في `UiElement.properties`. المصدر الوحيد لأسمائها. */
object PropertyKeys {
    const val NAME = "name"
    const val TEXT = "text"
    const val HINT = "hint"
    const val ITEMS = "items"
    const val ITEM_HEIGHT = "itemHeight"
    const val TEXT_COLOR = "textColor"
    const val FONT_SIZE = "fontSize"
    const val FONT_WEIGHT = "fontWeight"
    const val TEXT_ALIGN = "textAlign"
    const val BG_COLOR = "bgColor"
    const val BORDER_WIDTH = "borderWidth"
    const val BORDER_COLOR = "borderColor"
    const val CORNER_RADIUS = "cornerRadius"
    const val OPACITY = "opacity"
    const val VISIBLE = "visible"
    const val PADDING_START = "paddingStart"
    const val PADDING_TOP = "paddingTop"
    const val PADDING_END = "paddingEnd"
    const val PADDING_BOTTOM = "paddingBottom"
    const val MARGIN_START = "marginStart"
    const val MARGIN_TOP = "marginTop"
    const val MARGIN_END = "marginEnd"
    const val MARGIN_BOTTOM = "marginBottom"
}

object FontWeights {
    const val NORMAL = "normal"
    const val MEDIUM = "medium"
    const val BOLD = "bold"
}

object TextAligns {
    const val START = "start"
    const val CENTER = "center"
    const val END = "end"
}

/** تعريف خاصية واحدة: أين تنطبق، نوعها، وقيمتها الافتراضية لكل نوع عنصر. */
class PropertyDef(
    val key: String,
    @StringRes val labelRes: Int,
    val group: PropertyGroup,
    val kind: PropertyKind,
    val appliesTo: Set<String>,
    private val defaults: Map<String, String> = emptyMap(),
    private val fallback: String = ""
) {
    fun defaultFor(type: String): String = defaults[type] ?: fallback
}

/**
 * سجل الخصائص: التعريف الوحيد لكل خاصية. منه تُبنى لوحة الخصائص والقيم الافتراضية
 * وتحقق المدخلات، فلا يتكرر تعريف خاصية في أكثر من مكان.
 * الخصائص تُخزَّن في JSON ضمن `properties` كنصوص، فلا تغيير في مخطط الملف.
 */
object PropertyRegistry {

    // ألوان افتراضية (تطابق مظهر المرحلة 3).
    const val INK = "#FF1C1B1F"
    const val ACCENT = "#FF1B5E8C"
    const val HINT_GREY = "#FF6F7780"
    const val LINE = "#FFB0B7BF"
    const val IMAGE_FILL = "#FFE3E8EE"
    const val CARD_BORDER = "#FFE0E4EA"
    const val CONTAINER_FILL = "#145B9BD5"
    const val WHITE = "#FFFFFFFF"
    const val TRANSPARENT = "#00000000"

    private val ALL = ElementTypes.all.toSet()
    private val TEXTUAL = setOf(ElementTypes.TEXT, ElementTypes.BUTTON, ElementTypes.INPUT, ElementTypes.LIST)
    private val BOXED = setOf(
        ElementTypes.TEXT, ElementTypes.BUTTON, ElementTypes.IMAGE, ElementTypes.INPUT,
        ElementTypes.CONTAINER, ElementTypes.CARD, ElementTypes.LIST
    )
    private val PADDED = setOf(
        ElementTypes.TEXT, ElementTypes.BUTTON, ElementTypes.INPUT,
        ElementTypes.CONTAINER, ElementTypes.CARD, ElementTypes.LIST
    )

    private val DP_SMALL = PropertyKind.Integer(0, 200)

    private fun spacing(key: String, label: Int, types: Set<String>, defaults: Map<String, String> = emptyMap()) =
        PropertyDef(key, label, PropertyGroup.SPACING, DP_SMALL, types, defaults, "0")

    /** الترتيب هنا هو ترتيب الظهور داخل كل مجموعة. */
    val all: List<PropertyDef> = listOf(
        // عام
        PropertyDef(PropertyKeys.NAME, R.string.prop_name, PropertyGroup.GENERAL, PropertyKind.Text(), ALL),
        PropertyDef(PropertyKeys.VISIBLE, R.string.prop_visible, PropertyGroup.GENERAL, PropertyKind.Toggle, ALL, fallback = "true"),
        PropertyDef(PropertyKeys.OPACITY, R.string.prop_opacity, PropertyGroup.GENERAL, PropertyKind.Integer(0, 100), ALL, fallback = "100"),

        // نص
        PropertyDef(PropertyKeys.TEXT, R.string.prop_text, PropertyGroup.TEXT, PropertyKind.Text(), setOf(ElementTypes.TEXT, ElementTypes.BUTTON)),
        PropertyDef(PropertyKeys.HINT, R.string.prop_hint, PropertyGroup.TEXT, PropertyKind.Text(), setOf(ElementTypes.INPUT)),
        PropertyDef(
            PropertyKeys.ITEMS, R.string.prop_items, PropertyGroup.TEXT, PropertyKind.Text(multiline = true),
            setOf(ElementTypes.LIST), fallback = "عنصر 1\nعنصر 2\nعنصر 3"
        ),
        PropertyDef(PropertyKeys.ITEM_HEIGHT, R.string.prop_item_height, PropertyGroup.TEXT, PropertyKind.Integer(16, 120), setOf(ElementTypes.LIST), fallback = "40"),
        PropertyDef(
            PropertyKeys.TEXT_COLOR, R.string.prop_text_color, PropertyGroup.TEXT, PropertyKind.ColorValue, TEXTUAL,
            defaults = mapOf(ElementTypes.BUTTON to WHITE, ElementTypes.INPUT to HINT_GREY), fallback = INK
        ),
        PropertyDef(PropertyKeys.FONT_SIZE, R.string.prop_font_size, PropertyGroup.TEXT, PropertyKind.Integer(8, 72), TEXTUAL, fallback = "14"),
        PropertyDef(
            PropertyKeys.FONT_WEIGHT, R.string.prop_font_weight, PropertyGroup.TEXT,
            PropertyKind.Choice(
                listOf(
                    FontWeights.NORMAL to R.string.weight_normal,
                    FontWeights.MEDIUM to R.string.weight_medium,
                    FontWeights.BOLD to R.string.weight_bold
                )
            ),
            TEXTUAL, fallback = FontWeights.NORMAL
        ),
        PropertyDef(
            PropertyKeys.TEXT_ALIGN, R.string.prop_text_align, PropertyGroup.TEXT,
            PropertyKind.Choice(
                listOf(
                    TextAligns.START to R.string.align_start,
                    TextAligns.CENTER to R.string.align_center,
                    TextAligns.END to R.string.align_end
                )
            ),
            setOf(ElementTypes.TEXT, ElementTypes.BUTTON, ElementTypes.INPUT),
            defaults = mapOf(ElementTypes.BUTTON to TextAligns.CENTER), fallback = TextAligns.START
        ),

        // مظهر
        PropertyDef(
            PropertyKeys.BG_COLOR, R.string.prop_bg_color, PropertyGroup.APPEARANCE, PropertyKind.ColorValue,
            BOXED + ElementTypes.DIVIDER,
            defaults = mapOf(
                ElementTypes.BUTTON to ACCENT,
                ElementTypes.CARD to WHITE,
                ElementTypes.CONTAINER to CONTAINER_FILL,
                ElementTypes.IMAGE to IMAGE_FILL,
                ElementTypes.DIVIDER to LINE
            ),
            fallback = TRANSPARENT
        ),
        PropertyDef(
            PropertyKeys.BORDER_WIDTH, R.string.prop_border_width, PropertyGroup.APPEARANCE, PropertyKind.Integer(0, 24), BOXED,
            defaults = mapOf(ElementTypes.INPUT to "1", ElementTypes.CARD to "1"), fallback = "0"
        ),
        PropertyDef(
            PropertyKeys.BORDER_COLOR, R.string.prop_border_color, PropertyGroup.APPEARANCE, PropertyKind.ColorValue, BOXED,
            defaults = mapOf(ElementTypes.INPUT to HINT_GREY, ElementTypes.CARD to CARD_BORDER), fallback = LINE
        ),
        PropertyDef(
            PropertyKeys.CORNER_RADIUS, R.string.prop_corner_radius, PropertyGroup.APPEARANCE, PropertyKind.Integer(0, 100), BOXED,
            defaults = mapOf(
                ElementTypes.BUTTON to "8", ElementTypes.CARD to "12",
                ElementTypes.INPUT to "6", ElementTypes.IMAGE to "4"
            ),
            fallback = "0"
        ),

        // مسافات: الداخلية تؤثر على موضع المحتوى، والهوامش تُحفظ وتُستخدم في التخطيطات المرنة لاحقًا.
        spacing(PropertyKeys.PADDING_START, R.string.prop_padding_start, PADDED, mapOf(ElementTypes.BUTTON to "8", ElementTypes.INPUT to "12", ElementTypes.LIST to "12")),
        spacing(PropertyKeys.PADDING_END, R.string.prop_padding_end, PADDED, mapOf(ElementTypes.BUTTON to "8", ElementTypes.INPUT to "12", ElementTypes.LIST to "12")),
        spacing(PropertyKeys.PADDING_TOP, R.string.prop_padding_top, PADDED),
        spacing(PropertyKeys.PADDING_BOTTOM, R.string.prop_padding_bottom, PADDED),
        spacing(PropertyKeys.MARGIN_START, R.string.prop_margin_start, ALL),
        spacing(PropertyKeys.MARGIN_END, R.string.prop_margin_end, ALL),
        spacing(PropertyKeys.MARGIN_TOP, R.string.prop_margin_top, ALL),
        spacing(PropertyKeys.MARGIN_BOTTOM, R.string.prop_margin_bottom, ALL)
    )

    private val byKey: Map<String, PropertyDef> = all.associateBy { it.key }

    fun def(key: String): PropertyDef? = byKey[key]

    /** تعريفات الخصائص التي تنطبق على نوع العنصر، بترتيب العرض. */
    fun forType(type: String): List<PropertyDef> = all.filter { type in it.appliesTo }

    fun defaultOf(type: String, key: String): String = byKey[key]?.defaultFor(type).orEmpty()

    /**
     * يتحقق من قيمة مدخلة ويعيد الصيغة المخزنة، أو null إن كانت غير صالحة.
     * الأعداد تُقيَّد بالمدى، والألوان تُوحَّد إلى #AARRGGBB.
     */
    fun normalize(key: String, raw: String): String? {
        val def = byKey[key] ?: return null
        return when (val kind = def.kind) {
            is PropertyKind.Text -> raw.take(MAX_TEXT_LENGTH)
            is PropertyKind.Integer -> raw.trim().toIntOrNull()?.coerceIn(kind.min, kind.max)?.toString()
            PropertyKind.ColorValue -> ColorUtil.parse(raw)?.let { ColorUtil.format(it) }
            is PropertyKind.Choice -> raw.takeIf { v -> kind.options.any { it.first == v } }
            PropertyKind.Toggle -> raw.takeIf { it == "true" || it == "false" }
        }
    }

    private const val MAX_TEXT_LENGTH = 2000
}

/** ألوان بصيغة ARGB داخل Int (بلا اعتماد على Compose كي تُختبر على JVM). */
object ColorUtil {
    /** يقبل #RRGGBB (معتم) أو #AARRGGBB. يعيد null إن كان غير صالح. */
    fun parse(text: String): Int? {
        val s = text.trim().removePrefix("#")
        if (s.length != 6 && s.length != 8) return null
        if (!s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val value = s.toLong(16)
        return if (s.length == 6) (0xFF000000L or value).toInt() else value.toInt()
    }

    fun format(argb: Int): String = "#%08X".format(argb)
}

// ---- قراءة الخصائص مع القيم الافتراضية: نقطة الوصول الوحيدة لقيمة خاصية عنصر ----

fun UiElement.prop(key: String): String = properties[key] ?: PropertyRegistry.defaultOf(type, key)

fun UiElement.intProp(key: String): Int =
    prop(key).toIntOrNull() ?: PropertyRegistry.defaultOf(type, key).toIntOrNull() ?: 0

fun UiElement.boolProp(key: String): Boolean = prop(key) != "false"

fun UiElement.colorProp(key: String): Int =
    ColorUtil.parse(prop(key)) ?: ColorUtil.parse(PropertyRegistry.defaultOf(type, key)) ?: 0

fun UiElement.withProp(key: String, value: String): UiElement = copy(properties = properties + (key to value))
