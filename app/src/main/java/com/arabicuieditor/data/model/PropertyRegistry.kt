package com.arabicuieditor.data.model

import androidx.annotation.StringRes
import com.arabicuieditor.R

/** مجموعات الخصائص في لوحة الخصائص. الترتيب هنا هو ترتيب العرض. */
enum class PropertyGroup(@StringRes val labelRes: Int) {
    GENERAL(R.string.group_general),
    LAYOUT(R.string.group_layout),
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
    /** دوران العنصر بالدرجات (0..359) باتجاه عقارب الساعة حول مركزه. */
    const val ROTATION = "rotation"
    const val VISIBLE = "visible"
    const val PADDING_START = "paddingStart"
    const val PADDING_TOP = "paddingTop"
    const val PADDING_END = "paddingEnd"
    const val PADDING_BOTTOM = "paddingBottom"
    const val MARGIN_START = "marginStart"
    const val MARGIN_TOP = "marginTop"
    const val MARGIN_END = "marginEnd"
    const val MARGIN_BOTTOM = "marginBottom"

    // التخطيط: خصائص الحاوية (أو الشاشة) لترتيب أبنائها
    const val ARRANGEMENT = "arrangement"
    const val CHILD_ALIGN_H = "childAlignH"
    const val CHILD_ALIGN_V = "childAlignV"
    const val SPACING = "spacing"

    // التخطيط: خصائص العنصر نفسه داخل أبيه
    const val WEIGHT = "weight"
    const val SELF_ALIGN_H = "selfAlignH"
    const val SELF_ALIGN_V = "selfAlignV"
    const val PIN_H = "pinH"
    const val PIN_V = "pinV"
}

/** توزيع الأبناء على المحور الرئيسي للصف أو العمود. */
object Arrangements {
    const val START = "start"
    const val CENTER = "center"
    const val END = "end"
    const val BETWEEN = "between"
    const val AROUND = "around"
    const val EVENLY = "evenly"
}

/** محاذاة على محور واحد. للمحور الرأسي: START = أعلى وEND = أسفل. INHERIT لمحاذاة العنصر نفسه فقط. */
object Aligns {
    const val INHERIT = "inherit"
    const val START = "start"
    const val CENTER = "center"
    const val END = "end"
}

/** سلوك عنصر في تخطيط حر عند تغيّر حجم أبيه (حجم المعاينة) عن حجم التصميم. */
object Pins {
    const val START = "start"
    const val END = "end"
    const val CENTER = "center"
    const val STRETCH = "stretch"
    const val SCALE = "scale"
}

/** نوع «العنصر» الذي تُخزَّن عليه خصائص جذر الشاشة (يستعمل السجل نفسه). */
const val SCREEN_TYPE = "screen"

/** سياق ظهور الخاصية: تخطيط العنصر نفسه وتخطيط أبيه (للشاشة: الأب ABSOLUTE). */
data class PropertyContext(
    val ownLayout: ChildLayout = ChildLayout.ABSOLUTE,
    val parentLayout: ChildLayout = ChildLayout.ABSOLUTE,
    val isScreen: Boolean = false
)

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
    private val fallback: String = "",
    /** تُعرض الخاصية في اللوحة فقط حين يتحقق هذا الشرط (مثل: الوزن داخل صف أو عمود). */
    val visibleWhen: (PropertyContext) -> Boolean = { true }
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
    /** المسافات الداخلية تنطبق أيضًا على جذر الشاشة. */
    private val PADDED_OR_SCREEN = PADDED + SCREEN_TYPE

    private val DP_SMALL = PropertyKind.Integer(0, 200)

    private val CONTAINERS = setOf(ElementTypes.CONTAINER, ElementTypes.CARD, SCREEN_TYPE)
    private val FLOW = setOf(ChildLayout.COLUMN, ChildLayout.ROW)
    private val STACKED = setOf(ChildLayout.COLUMN, ChildLayout.ROW, ChildLayout.BOX)

    private val ARRANGEMENT_OPTIONS = listOf(
        Arrangements.START to R.string.arr_start,
        Arrangements.CENTER to R.string.arr_center,
        Arrangements.END to R.string.arr_end,
        Arrangements.BETWEEN to R.string.arr_between,
        Arrangements.AROUND to R.string.arr_around,
        Arrangements.EVENLY to R.string.arr_evenly
    )
    private val H_ALIGN_OPTIONS = listOf(
        Aligns.START to R.string.align_start,
        Aligns.CENTER to R.string.align_center,
        Aligns.END to R.string.align_end
    )
    private val V_ALIGN_OPTIONS = listOf(
        Aligns.START to R.string.valign_top,
        Aligns.CENTER to R.string.align_center,
        Aligns.END to R.string.valign_bottom
    )

    private fun spacing(
        key: String,
        label: Int,
        types: Set<String>,
        defaults: Map<String, String> = emptyMap(),
        visibleWhen: (PropertyContext) -> Boolean = { true }
    ) = PropertyDef(key, label, PropertyGroup.SPACING, DP_SMALL, types, defaults, "0", visibleWhen)

    /** المسافات الداخلية لجذر الشاشة لا معنى لها في التخطيط الحر؛ أما العناصر فتُعرض دائمًا. */
    private val paddingVisible: (PropertyContext) -> Boolean = { !it.isScreen || it.ownLayout in STACKED }

    /** الترتيب هنا هو ترتيب الظهور داخل كل مجموعة. */
    val all: List<PropertyDef> = listOf(
        // عام
        PropertyDef(PropertyKeys.NAME, R.string.prop_name, PropertyGroup.GENERAL, PropertyKind.Text(), ALL),
        PropertyDef(PropertyKeys.VISIBLE, R.string.prop_visible, PropertyGroup.GENERAL, PropertyKind.Toggle, ALL, fallback = "true"),
        PropertyDef(PropertyKeys.OPACITY, R.string.prop_opacity, PropertyGroup.GENERAL, PropertyKind.Integer(0, 100), ALL, fallback = "100"),
        PropertyDef(PropertyKeys.ROTATION, R.string.prop_rotation, PropertyGroup.GENERAL, PropertyKind.Integer(0, 359), ALL, fallback = "0"),

        // تخطيط: ترتيب الأبناء (للحاوية أو الشاشة)
        PropertyDef(
            PropertyKeys.ARRANGEMENT, R.string.prop_arrangement, PropertyGroup.LAYOUT,
            PropertyKind.Choice(ARRANGEMENT_OPTIONS), CONTAINERS, fallback = Arrangements.START,
            visibleWhen = { it.ownLayout in FLOW }
        ),
        PropertyDef(
            PropertyKeys.CHILD_ALIGN_H, R.string.prop_child_align_h, PropertyGroup.LAYOUT,
            PropertyKind.Choice(H_ALIGN_OPTIONS), CONTAINERS, fallback = Aligns.START,
            visibleWhen = { it.ownLayout == ChildLayout.COLUMN || it.ownLayout == ChildLayout.BOX }
        ),
        PropertyDef(
            PropertyKeys.CHILD_ALIGN_V, R.string.prop_child_align_v, PropertyGroup.LAYOUT,
            PropertyKind.Choice(V_ALIGN_OPTIONS), CONTAINERS, fallback = Aligns.START,
            visibleWhen = { it.ownLayout == ChildLayout.ROW || it.ownLayout == ChildLayout.BOX }
        ),
        PropertyDef(
            PropertyKeys.SPACING, R.string.prop_spacing, PropertyGroup.LAYOUT,
            PropertyKind.Integer(0, 200), CONTAINERS, fallback = "0",
            visibleWhen = { it.ownLayout in FLOW }
        ),

        // تخطيط: سلوك العنصر داخل أبيه
        PropertyDef(
            PropertyKeys.WEIGHT, R.string.prop_weight, PropertyGroup.LAYOUT,
            PropertyKind.Integer(0, 10), ALL, fallback = "0",
            visibleWhen = { it.parentLayout in FLOW }
        ),
        PropertyDef(
            PropertyKeys.SELF_ALIGN_H, R.string.prop_self_align_h, PropertyGroup.LAYOUT,
            PropertyKind.Choice(listOf(Aligns.INHERIT to R.string.self_inherit) + H_ALIGN_OPTIONS), ALL,
            fallback = Aligns.INHERIT,
            visibleWhen = { it.parentLayout == ChildLayout.COLUMN || it.parentLayout == ChildLayout.BOX }
        ),
        PropertyDef(
            PropertyKeys.SELF_ALIGN_V, R.string.prop_self_align_v, PropertyGroup.LAYOUT,
            PropertyKind.Choice(listOf(Aligns.INHERIT to R.string.self_inherit) + V_ALIGN_OPTIONS), ALL,
            fallback = Aligns.INHERIT,
            visibleWhen = { it.parentLayout == ChildLayout.ROW || it.parentLayout == ChildLayout.BOX }
        ),
        PropertyDef(
            PropertyKeys.PIN_H, R.string.prop_pin_h, PropertyGroup.LAYOUT,
            PropertyKind.Choice(
                listOf(
                    Pins.START to R.string.pin_start,
                    Pins.END to R.string.pin_end,
                    Pins.CENTER to R.string.pin_center,
                    Pins.STRETCH to R.string.pin_stretch,
                    Pins.SCALE to R.string.pin_scale
                )
            ),
            ALL, fallback = Pins.START,
            visibleWhen = { it.parentLayout == ChildLayout.ABSOLUTE }
        ),
        PropertyDef(
            PropertyKeys.PIN_V, R.string.prop_pin_v, PropertyGroup.LAYOUT,
            PropertyKind.Choice(
                listOf(
                    Pins.START to R.string.pin_top,
                    Pins.END to R.string.pin_bottom,
                    Pins.CENTER to R.string.pin_center,
                    Pins.STRETCH to R.string.pin_stretch,
                    Pins.SCALE to R.string.pin_scale
                )
            ),
            ALL, fallback = Pins.START,
            visibleWhen = { it.parentLayout == ChildLayout.ABSOLUTE }
        ),

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
            defaults = mapOf(ElementTypes.BUTTON to Tokens.color(Tokens.ON_PRIMARY), ElementTypes.INPUT to Tokens.color(Tokens.MUTED)),
            fallback = Tokens.color(Tokens.TEXT_COLOR)
        ),
        PropertyDef(PropertyKeys.FONT_SIZE, R.string.prop_font_size, PropertyGroup.TEXT, PropertyKind.Integer(8, 72), TEXTUAL, fallback = Tokens.text(Tokens.BODY)),
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
                ElementTypes.BUTTON to Tokens.color(Tokens.PRIMARY),
                ElementTypes.CARD to Tokens.color(Tokens.SURFACE),
                ElementTypes.CONTAINER to CONTAINER_FILL,
                ElementTypes.IMAGE to IMAGE_FILL,
                ElementTypes.DIVIDER to Tokens.color(Tokens.BORDER)
            ),
            fallback = TRANSPARENT
        ),
        PropertyDef(
            PropertyKeys.BORDER_WIDTH, R.string.prop_border_width, PropertyGroup.APPEARANCE, PropertyKind.Integer(0, 24), BOXED,
            defaults = mapOf(ElementTypes.INPUT to "1", ElementTypes.CARD to "1"), fallback = "0"
        ),
        PropertyDef(
            PropertyKeys.BORDER_COLOR, R.string.prop_border_color, PropertyGroup.APPEARANCE, PropertyKind.ColorValue, BOXED,
            defaults = mapOf(ElementTypes.INPUT to Tokens.color(Tokens.MUTED), ElementTypes.CARD to Tokens.color(Tokens.BORDER)), fallback = LINE
        ),
        PropertyDef(
            PropertyKeys.CORNER_RADIUS, R.string.prop_corner_radius, PropertyGroup.APPEARANCE, PropertyKind.Integer(0, 100), BOXED,
            defaults = mapOf(
                ElementTypes.BUTTON to Tokens.radius(Tokens.MEDIUM), ElementTypes.CARD to Tokens.radius(Tokens.LARGE),
                ElementTypes.INPUT to Tokens.radius(Tokens.SMALL), ElementTypes.IMAGE to "4"
            ),
            fallback = "0"
        ),

        // مسافات: الداخلية تؤثر على موضع المحتوى، والهوامش تُحفظ وتُستخدم في التخطيطات المرنة لاحقًا.
        spacing(PropertyKeys.PADDING_START, R.string.prop_padding_start, PADDED_OR_SCREEN, mapOf(ElementTypes.BUTTON to "8", ElementTypes.INPUT to "12", ElementTypes.LIST to "12"), paddingVisible),
        spacing(PropertyKeys.PADDING_END, R.string.prop_padding_end, PADDED_OR_SCREEN, mapOf(ElementTypes.BUTTON to "8", ElementTypes.INPUT to "12", ElementTypes.LIST to "12"), paddingVisible),
        spacing(PropertyKeys.PADDING_TOP, R.string.prop_padding_top, PADDED_OR_SCREEN, visibleWhen = paddingVisible),
        spacing(PropertyKeys.PADDING_BOTTOM, R.string.prop_padding_bottom, PADDED_OR_SCREEN, visibleWhen = paddingVisible),
        spacing(PropertyKeys.MARGIN_START, R.string.prop_margin_start, ALL),
        spacing(PropertyKeys.MARGIN_END, R.string.prop_margin_end, ALL),
        spacing(PropertyKeys.MARGIN_TOP, R.string.prop_margin_top, ALL),
        spacing(PropertyKeys.MARGIN_BOTTOM, R.string.prop_margin_bottom, ALL)
    )

    private val byKey: Map<String, PropertyDef> = all.associateBy { it.key }

    fun def(key: String): PropertyDef? = byKey[key]

    /** تعريفات الخصائص التي تنطبق على نوع العنصر، بترتيب العرض. */
    fun forType(type: String): List<PropertyDef> = all.filter { type in it.appliesTo }

    /** كـ [forType] لكن بعد تطبيق شروط الظهور (مثل: الوزن لا يظهر إلا داخل صف أو عمود). */
    fun forContext(type: String, context: PropertyContext): List<PropertyDef> =
        all.filter { type in it.appliesTo && it.visibleWhen(context) }

    fun defaultOf(type: String, key: String): String = byKey[key]?.defaultFor(type).orEmpty()

    /**
     * يتحقق من قيمة مدخلة ويعيد الصيغة المخزنة، أو null إن كانت غير صالحة.
     * الأعداد تُقيَّد بالمدى، والألوان تُوحَّد إلى #AARRGGBB.
     */
    fun normalize(key: String, raw: String): String? {
        val def = byKey[key] ?: return null
        return when (val kind = def.kind) {
            is PropertyKind.Text -> raw.take(MAX_TEXT_LENGTH)
            is PropertyKind.Integer ->
                if (Tokens.isToken(raw.trim())) raw.trim().takeIf { Tokens.accepts(key, kind, it) }
                else raw.trim().toIntOrNull()?.coerceIn(kind.min, kind.max)?.toString()
            PropertyKind.ColorValue ->
                if (Tokens.isToken(raw.trim())) raw.trim().takeIf { Tokens.accepts(key, kind, it) }
                else ColorUtil.parse(raw)?.let { ColorUtil.format(it) }
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
    prop(key).let { it.toIntOrNull() ?: Tokens.resolveInt(it) }
        ?: PropertyRegistry.defaultOf(type, key).let { it.toIntOrNull() ?: Tokens.resolveInt(it) } ?: 0

fun UiElement.boolProp(key: String): Boolean = prop(key) != "false"

fun UiElement.colorProp(key: String): Int =
    prop(key).let { ColorUtil.parse(it) ?: Tokens.resolveColor(it) }
        ?: PropertyRegistry.defaultOf(type, key).let { ColorUtil.parse(it) ?: Tokens.resolveColor(it) } ?: 0

fun UiElement.withProp(key: String, value: String): UiElement = copy(properties = properties + (key to value))

// ---- خصائص جذر الشاشة (تستعمل السجل نفسه بنوع SCREEN_TYPE) ----

fun UiScreen.prop(key: String): String = properties[key] ?: PropertyRegistry.defaultOf(SCREEN_TYPE, key)

fun UiScreen.intProp(key: String): Int =
    prop(key).toIntOrNull() ?: PropertyRegistry.defaultOf(SCREEN_TYPE, key).toIntOrNull() ?: 0

fun UiScreen.withProp(key: String, value: String): UiScreen = copy(properties = properties + (key to value))
