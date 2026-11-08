package com.arabicuieditor.data.model

import com.arabicuieditor.R

/**
 * أزرار جاهزة بأنماط فنية (ثلاثي الأبعاد، زجاجي، نيون، معدني، متدرج، متوهج، ناعم). تصميمها أصلي:
 * أيقونات الفهرس المدمج وألوان المشروع. أزرار أيقونة دائرية وأزرار «حبة» بنص؛ وكلها أزرار حقيقية
 * قابلة للتعديل (نص أو أيقونة ولون ونمط وإجراء نقر) بتأثير ضغط «تصغير» افتراضيًا.
 */
internal object StyledButtons {
    private fun base(w: Float, h: Float, radius: Int, bg: String, style: String, extra: Map<String, String>) = UiElement(
        id = newId(),
        type = ElementTypes.BUTTON,
        layout = ElementLayout(0f, 0f, SizeSpec.fixed(w), SizeSpec.fixed(h)),
        properties = mapOf(
            PropertyKeys.BG_COLOR to bg,
            PropertyKeys.CORNER_RADIUS to "$radius",
            PropertyKeys.BUTTON_STYLE to style,
            PropertyKeys.PRESS_EFFECT to PressEffects.SHRINK
        ) + extra
    )

    /** زر أيقونة دائري. الأيقونة بيضاء إلا في النيون (بلون الزر) والزجاجي والمعدني (داكنة). */
    private fun iconButton(icon: String, size: Float, bg: String, style: String, iconColor: String) =
        base(size, size, (size / 2).toInt(), bg, style, mapOf(PropertyKeys.ICON to icon, PropertyKeys.ICON_COLOR to iconColor))

    private fun pill(text: String, bg: String, fg: String, style: String) =
        base(140f, 44f, 22, bg, style, mapOf(PropertyKeys.TEXT to text, PropertyKeys.TEXT_COLOR to fg, PropertyKeys.FONT_SIZE to "16"))

    val presets: List<ComponentPreset> = listOf(
        ComponentPreset("sb_check_3d", R.string.sb_check_3d, PresetCategory.BUTTONS) { _ -> listOf(iconButton("Check", 56f, "#2E9E5B", "raised3d", "#FFFFFF")) },
        ComponentPreset("sb_close_3d", R.string.sb_close_3d, PresetCategory.BUTTONS) { _ -> listOf(iconButton("Close", 56f, "#D9534F", "raised3d", "#FFFFFF")) },
        ComponentPreset("sb_star_glow", R.string.sb_star_glow, PresetCategory.BUTTONS) { _ -> listOf(iconButton("Star", 56f, "#F2B632", "glow", "#FFFFFF")) },
        ComponentPreset("sb_heart_soft", R.string.sb_heart_soft, PresetCategory.BUTTONS) { _ -> listOf(iconButton("Favorite", 56f, "#EC6F9A", "soft", "#FFFFFF")) },
        ComponentPreset("sb_arrow_neon", R.string.sb_arrow_neon, PresetCategory.BUTTONS) { _ -> listOf(iconButton("KeyboardArrowRight", 56f, "#18D6E0", "neon", "#18D6E0")) },
        ComponentPreset("sb_help_glass", R.string.sb_help_glass, PresetCategory.BUTTONS) { _ -> listOf(iconButton("Info", 56f, "#4A7DFF", "glass", "#1F3FA8")) },
        ComponentPreset("sb_warn_metal", R.string.sb_warn_metal, PresetCategory.BUTTONS) { _ -> listOf(iconButton("Warning", 56f, "#9AA3AB", "metallic", "#202B35")) },
        ComponentPreset("sb_play_gradient", R.string.sb_play_gradient, PresetCategory.BUTTONS) { _ -> listOf(iconButton("PlayArrow", 56f, "#23866B", "gradient", "#FFFFFF")) },
        ComponentPreset("sb_pin_3d", R.string.sb_pin_3d, PresetCategory.BUTTONS) { _ -> listOf(iconButton("LocationOn", 56f, "#E5483F", "raised3d", "#FFFFFF")) },
        ComponentPreset("sb_bell_glow", R.string.sb_bell_glow, PresetCategory.BUTTONS) { _ -> listOf(iconButton("Notifications", 56f, "#F28C28", "glow", "#FFFFFF")) },
        ComponentPreset("sb_like_3d", R.string.sb_like_3d, PresetCategory.BUTTONS) { _ -> listOf(iconButton("ThumbUp", 56f, "#7CB518", "raised3d", "#FFFFFF")) },
        ComponentPreset("sb_add_3d", R.string.sb_add_3d, PresetCategory.BUTTONS) { _ -> listOf(iconButton("Add", 56f, "#3B6FE0", "raised3d", "#FFFFFF")) },
        ComponentPreset("sb_pill_gradient", R.string.sb_pill_gradient, PresetCategory.BUTTONS) { t -> listOf(pill(t(R.string.sb_pill_text), "#23866B", "#FFFFFF", "gradient")) },
        ComponentPreset("sb_pill_3d", R.string.sb_pill_3d, PresetCategory.BUTTONS) { t -> listOf(pill(t(R.string.sb_pill_text), "#4A7DFF", "#FFFFFF", "raised3d")) },
        ComponentPreset("sb_pill_glass", R.string.sb_pill_glass, PresetCategory.BUTTONS) { t -> listOf(pill(t(R.string.sb_pill_text), "#23866B", "#14503F", "glass")) },
        ComponentPreset("sb_pill_neon", R.string.sb_pill_neon, PresetCategory.BUTTONS) { t -> listOf(pill(t(R.string.sb_pill_text), "#B14CFF", "#B14CFF", "neon")) },
        ComponentPreset("sb_pill_metal", R.string.sb_pill_metal, PresetCategory.BUTTONS) { t -> listOf(pill(t(R.string.sb_pill_text), "#9AA3AB", "#202B35", "metallic")) },
        ComponentPreset("sb_pill_glow", R.string.sb_pill_glow, PresetCategory.BUTTONS) { t -> listOf(pill(t(R.string.sb_pill_text), "#F2B632", "#FFFFFF", "glow")) }
    )
}
