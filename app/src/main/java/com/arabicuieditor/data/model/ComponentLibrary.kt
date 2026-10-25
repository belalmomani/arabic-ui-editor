package com.arabicuieditor.data.model

import androidx.annotation.StringRes
import com.arabicuieditor.R

enum class PresetCategory(@StringRes val labelRes: Int) {
    BUTTONS(R.string.cat_buttons),
    CARDS(R.string.cat_cards),
    INPUTS(R.string.cat_inputs),
    TEXTS(R.string.cat_texts),
    IMAGES(R.string.cat_images),
    LISTS(R.string.cat_lists),
    LAYOUTS(R.string.cat_layouts)
}

/**
 * مكوّن جاهز: دالة تبني عنصرًا أو عدة عناصر (مسطّحة) عند الأصل (0,0).
 * الإحداثيات النسبية بين عناصر المكوّن محفوظة، ويتولى المحرر وضعه في مكانه.
 * العنصر الأول في القائمة هو الذي يُحدَّد بعد الإضافة.
 */
class ComponentPreset(
    val id: String,
    @StringRes val labelRes: Int,
    val category: PresetCategory,
    val build: (text: (Int) -> String) -> List<UiElement>
)

/**
 * مكتبة المكونات الأولية. تُبنى كلها من الخصائص نفسها في [PropertyRegistry]،
 * فلا يوجد تعريف مظهر خاص بالمكتبة.
 */
object ComponentLibrary {

    private fun el(
        type: String,
        w: Float,
        h: Float,
        x: Float = 0f,
        y: Float = 0f,
        vararg props: Pair<String, String>
    ) = UiElement(
        id = newId(),
        type = type,
        layout = ElementLayout(x, y, SizeSpec.fixed(w), SizeSpec.fixed(h)),
        properties = mapOf(*props)
    )

    /** عنصر داخل حاوية مرنة: موضعه تحدده الحاوية لا (x, y). */
    private fun child(type: String, w: SizeSpec, h: SizeSpec, props: Map<String, String> = emptyMap()) =
        UiElement(id = newId(), type = type, layout = ElementLayout(0f, 0f, w, h), properties = props)

    /** حاوية بتخطيط مرن وأبناء جاهزين. */
    private fun group(
        type: String,
        layout: ChildLayout,
        w: SizeSpec,
        h: SizeSpec,
        children: List<UiElement>,
        props: Map<String, String> = emptyMap()
    ) = UiElement(
        id = newId(),
        type = type,
        layout = ElementLayout(0f, 0f, w, h),
        childLayout = layout,
        properties = props,
        children = children
    )

    private fun padAll(v: Int) = mapOf(
        PropertyKeys.PADDING_START to "$v", PropertyKeys.PADDING_END to "$v",
        PropertyKeys.PADDING_TOP to "$v", PropertyKeys.PADDING_BOTTOM to "$v"
    )

    private fun fill() = SizeSpec(SizeMode.MATCH_PARENT, 200f)

    private const val DANGER = "#FFB3261E"
    private const val MUTED = "#FF5F6B76"

    val presets: List<ComponentPreset> = listOf(
        // أزرار
        ComponentPreset("btn_primary", R.string.preset_btn_primary, PresetCategory.BUTTONS) { t ->
            listOf(el(ElementTypes.BUTTON, 140f, 44f, props = arrayOf(PropertyKeys.TEXT to t(R.string.preset_btn_primary))))
        },
        ComponentPreset("btn_outline", R.string.preset_btn_outline, PresetCategory.BUTTONS) { t ->
            listOf(
                el(
                    ElementTypes.BUTTON, 140f, 44f,
                    props = arrayOf(
                        PropertyKeys.TEXT to t(R.string.preset_btn_outline),
                        PropertyKeys.BG_COLOR to PropertyRegistry.TRANSPARENT,
                        PropertyKeys.TEXT_COLOR to PropertyRegistry.ACCENT,
                        PropertyKeys.BORDER_WIDTH to "2",
                        PropertyKeys.BORDER_COLOR to PropertyRegistry.ACCENT
                    )
                )
            )
        },
        ComponentPreset("btn_text", R.string.preset_btn_text, PresetCategory.BUTTONS) { t ->
            listOf(
                el(
                    ElementTypes.BUTTON, 120f, 40f,
                    props = arrayOf(
                        PropertyKeys.TEXT to t(R.string.preset_btn_text),
                        PropertyKeys.BG_COLOR to PropertyRegistry.TRANSPARENT,
                        PropertyKeys.TEXT_COLOR to PropertyRegistry.ACCENT
                    )
                )
            )
        },
        ComponentPreset("btn_danger", R.string.preset_btn_danger, PresetCategory.BUTTONS) { t ->
            listOf(
                el(
                    ElementTypes.BUTTON, 140f, 44f,
                    props = arrayOf(
                        PropertyKeys.TEXT to t(R.string.preset_btn_danger),
                        PropertyKeys.BG_COLOR to DANGER
                    )
                )
            )
        },

        // بطاقات
        ComponentPreset("card_basic", R.string.preset_card_basic, PresetCategory.CARDS) {
            listOf(el(ElementTypes.CARD, 220f, 120f))
        },
        ComponentPreset("card_title_body", R.string.preset_card_title_body, PresetCategory.CARDS) { t ->
            listOf(
                el(ElementTypes.CARD, 240f, 120f),
                el(
                    ElementTypes.TEXT, 208f, 26f, 16f, 14f,
                    PropertyKeys.TEXT to t(R.string.preset_card_title),
                    PropertyKeys.FONT_SIZE to "18",
                    PropertyKeys.FONT_WEIGHT to FontWeights.BOLD
                ),
                el(
                    ElementTypes.TEXT, 208f, 56f, 16f, 46f,
                    PropertyKeys.TEXT to t(R.string.preset_card_body),
                    PropertyKeys.TEXT_COLOR to MUTED
                )
            )
        },

        // حقول إدخال
        ComponentPreset("input_basic", R.string.preset_input_basic, PresetCategory.INPUTS) { t ->
            listOf(el(ElementTypes.INPUT, 220f, 48f, props = arrayOf(PropertyKeys.HINT to t(R.string.preset_input_hint))))
        },
        ComponentPreset("input_labeled", R.string.preset_input_labeled, PresetCategory.INPUTS) { t ->
            listOf(
                el(
                    ElementTypes.TEXT, 220f, 20f, 0f, 0f,
                    PropertyKeys.TEXT to t(R.string.preset_input_label),
                    PropertyKeys.FONT_SIZE to "12",
                    PropertyKeys.TEXT_COLOR to MUTED
                ),
                el(ElementTypes.INPUT, 220f, 48f, 0f, 24f, PropertyKeys.HINT to t(R.string.preset_input_hint))
            )
        },

        // نصوص
        ComponentPreset("text_heading", R.string.preset_text_heading, PresetCategory.TEXTS) { t ->
            listOf(
                el(
                    ElementTypes.TEXT, 240f, 36f,
                    props = arrayOf(
                        PropertyKeys.TEXT to t(R.string.preset_text_heading),
                        PropertyKeys.FONT_SIZE to "24",
                        PropertyKeys.FONT_WEIGHT to FontWeights.BOLD
                    )
                )
            )
        },
        ComponentPreset("text_body", R.string.preset_text_body, PresetCategory.TEXTS) { t ->
            listOf(el(ElementTypes.TEXT, 240f, 48f, props = arrayOf(PropertyKeys.TEXT to t(R.string.preset_text_body_sample))))
        },
        ComponentPreset("text_caption", R.string.preset_text_caption, PresetCategory.TEXTS) { t ->
            listOf(
                el(
                    ElementTypes.TEXT, 200f, 20f,
                    props = arrayOf(
                        PropertyKeys.TEXT to t(R.string.preset_text_caption),
                        PropertyKeys.FONT_SIZE to "12",
                        PropertyKeys.TEXT_COLOR to MUTED
                    )
                )
            )
        },

        // صور
        ComponentPreset("image_wide", R.string.preset_image_wide, PresetCategory.IMAGES) {
            listOf(el(ElementTypes.IMAGE, 220f, 124f))
        },
        ComponentPreset("image_avatar", R.string.preset_image_avatar, PresetCategory.IMAGES) {
            listOf(el(ElementTypes.IMAGE, 80f, 80f, props = arrayOf(PropertyKeys.CORNER_RADIUS to "40")))
        },

        // قوائم
        ComponentPreset("list_basic", R.string.preset_list_basic, PresetCategory.LISTS) {
            listOf(el(ElementTypes.LIST, 220f, 136f))
        },
        // تخطيطات مرنة: حاويات بأبناء مرتبين (صف، عمود، صندوق)
        ComponentPreset("layout_row", R.string.preset_layout_row, PresetCategory.LAYOUTS) { t ->
            listOf(
                group(
                    ElementTypes.CONTAINER, ChildLayout.ROW, SizeSpec.fixed(300f), SizeSpec.fixed(64f),
                    listOf(
                        child(
                            ElementTypes.BUTTON, SizeSpec.fixed(100f), SizeSpec.fixed(44f),
                            mapOf(PropertyKeys.TEXT to t(R.string.preset_layout_btn_a), PropertyKeys.WEIGHT to "1")
                        ),
                        child(
                            ElementTypes.BUTTON, SizeSpec.fixed(100f), SizeSpec.fixed(44f),
                            mapOf(
                                PropertyKeys.TEXT to t(R.string.preset_layout_btn_b),
                                PropertyKeys.WEIGHT to "1",
                                PropertyKeys.BG_COLOR to MUTED
                            )
                        )
                    ),
                    padAll(8) + mapOf(PropertyKeys.SPACING to "8", PropertyKeys.CHILD_ALIGN_V to Aligns.CENTER)
                )
            )
        },
        ComponentPreset("layout_column", R.string.preset_layout_column, PresetCategory.LAYOUTS) { t ->
            listOf(
                group(
                    ElementTypes.CONTAINER, ChildLayout.COLUMN, SizeSpec.fixed(260f), SizeSpec.fixed(190f),
                    listOf(
                        child(
                            ElementTypes.TEXT, fill(), SizeSpec.fixed(28f),
                            mapOf(PropertyKeys.TEXT to t(R.string.preset_layout_field), PropertyKeys.FONT_WEIGHT to FontWeights.BOLD)
                        ),
                        child(ElementTypes.INPUT, fill(), SizeSpec.fixed(48f), mapOf(PropertyKeys.HINT to t(R.string.preset_input_hint))),
                        child(ElementTypes.BUTTON, fill(), SizeSpec.fixed(44f), mapOf(PropertyKeys.TEXT to t(R.string.preset_layout_btn_a)))
                    ),
                    padAll(12) + mapOf(PropertyKeys.SPACING to "8")
                )
            )
        },
        ComponentPreset("layout_box", R.string.preset_layout_box, PresetCategory.LAYOUTS) { t ->
            listOf(
                group(
                    ElementTypes.CONTAINER, ChildLayout.BOX, SizeSpec.fixed(260f), SizeSpec.fixed(140f),
                    listOf(
                        child(
                            ElementTypes.TEXT, SizeSpec.fixed(160f), SizeSpec.fixed(28f),
                            mapOf(PropertyKeys.TEXT to t(R.string.preset_layout_title), PropertyKeys.TEXT_ALIGN to TextAligns.CENTER)
                        )
                    ),
                    mapOf(PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER, PropertyKeys.CHILD_ALIGN_V to Aligns.CENTER)
                )
            )
        },
        ComponentPreset("layout_card", R.string.preset_layout_card, PresetCategory.LAYOUTS) { t ->
            listOf(
                group(
                    ElementTypes.CARD, ChildLayout.COLUMN, SizeSpec.fixed(280f), SizeSpec.fixed(170f),
                    listOf(
                        child(
                            ElementTypes.TEXT, fill(), SizeSpec.fixed(26f),
                            mapOf(
                                PropertyKeys.TEXT to t(R.string.preset_card_title),
                                PropertyKeys.FONT_SIZE to "18",
                                PropertyKeys.FONT_WEIGHT to FontWeights.BOLD
                            )
                        ),
                        child(
                            ElementTypes.TEXT, fill(), SizeSpec.fixed(44f),
                            mapOf(PropertyKeys.TEXT to t(R.string.preset_card_body), PropertyKeys.TEXT_COLOR to MUTED)
                        ),
                        child(
                            ElementTypes.BUTTON, SizeSpec.fixed(120f), SizeSpec.fixed(40f),
                            mapOf(PropertyKeys.TEXT to t(R.string.preset_layout_btn_a), PropertyKeys.SELF_ALIGN_H to Aligns.END)
                        )
                    ),
                    padAll(16) + mapOf(PropertyKeys.SPACING to "8")
                )
            )
        },
        ComponentPreset("layout_toolbar", R.string.preset_layout_toolbar, PresetCategory.LAYOUTS) { t ->
            listOf(
                group(
                    ElementTypes.CONTAINER, ChildLayout.ROW, fill(), SizeSpec.fixed(56f),
                    listOf(
                        child(
                            ElementTypes.TEXT, SizeSpec.fixed(140f), SizeSpec.fixed(28f),
                            mapOf(
                                PropertyKeys.TEXT to t(R.string.preset_layout_title),
                                PropertyKeys.TEXT_COLOR to PropertyRegistry.WHITE,
                                PropertyKeys.FONT_SIZE to "18",
                                PropertyKeys.FONT_WEIGHT to FontWeights.BOLD
                            )
                        ),
                        child(
                            ElementTypes.BUTTON, SizeSpec.fixed(80f), SizeSpec.fixed(36f),
                            mapOf(
                                PropertyKeys.TEXT to t(R.string.preset_layout_btn_a),
                                PropertyKeys.BG_COLOR to PropertyRegistry.TRANSPARENT
                            )
                        )
                    ),
                    padAll(10) + mapOf(
                        PropertyKeys.BG_COLOR to PropertyRegistry.ACCENT,
                        PropertyKeys.ARRANGEMENT to Arrangements.BETWEEN,
                        PropertyKeys.CHILD_ALIGN_V to Aligns.CENTER
                    )
                )
            )
        },

        ComponentPreset("list_compact", R.string.preset_list_compact, PresetCategory.LISTS) { t ->
            listOf(
                el(
                    ElementTypes.LIST, 220f, 132f,
                    props = arrayOf(
                        PropertyKeys.ITEMS to t(R.string.preset_list_items),
                        PropertyKeys.ITEM_HEIGHT to "32",
                        PropertyKeys.FONT_SIZE to "13"
                    )
                )
            )
        }
    )

    fun byCategory(category: PresetCategory): List<ComponentPreset> = presets.filter { it.category == category }
}
