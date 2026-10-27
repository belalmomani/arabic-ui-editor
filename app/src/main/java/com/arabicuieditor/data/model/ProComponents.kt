package com.arabicuieditor.data.model

import com.arabicuieditor.R

/**
 * مكتبة المكوّنات الاحترافية: مكوّنات مركّبة جاهزة (تسجيل الدخول، المنتجات، التنقل، القوائم،
 * النوافذ المنبثقة، الأخبار، المحادثات). تُبنى كلها من العناصر والحاويات المرنة والخصائص المسجّلة
 * في [PropertyRegistry]، فكل جزء منها قابل للتحديد والتعديل بعد الإضافة.
 */
internal object ProComponents {

    private const val MUTED = "#FF5F6B76"
    private const val DANGER = "#FFB3261E"
    private const val SOFT = "#FFEEF2F6"

    // ---- أدوات بناء ----

    private fun fixed(v: Float) = SizeSpec.fixed(v)
    private fun fill() = SizeSpec(SizeMode.MATCH_PARENT, 200f)

    private fun node(
        type: String,
        w: SizeSpec,
        h: SizeSpec,
        props: Map<String, String> = emptyMap(),
        layout: ChildLayout = ChildLayout.ABSOLUTE,
        children: List<UiElement> = emptyList()
    ) = UiElement(
        id = newId(),
        type = type,
        layout = ElementLayout(0f, 0f, w, h),
        childLayout = layout,
        properties = props,
        children = children
    )

    private fun pad(v: Int) = mapOf(
        PropertyKeys.PADDING_START to "$v", PropertyKeys.PADDING_END to "$v",
        PropertyKeys.PADDING_TOP to "$v", PropertyKeys.PADDING_BOTTOM to "$v"
    )

    /** الحاوية العادية بلا تلوين افتراضي (التلوين الأزرق الشفاف للحاوية يخص التصميم اليدوي). */
    private fun plain(type: String): Map<String, String> =
        if (type == ElementTypes.CONTAINER) mapOf(PropertyKeys.BG_COLOR to PropertyRegistry.TRANSPARENT) else emptyMap()

    private fun text(
        value: String,
        w: SizeSpec,
        h: Float,
        size: Int = 14,
        bold: Boolean = false,
        color: String? = null,
        align: String? = null,
        weight: Int = 0
    ): UiElement {
        val p = mutableMapOf(PropertyKeys.TEXT to value, PropertyKeys.FONT_SIZE to "$size")
        if (bold) p[PropertyKeys.FONT_WEIGHT] = FontWeights.BOLD
        if (color != null) p[PropertyKeys.TEXT_COLOR] = color
        if (align != null) p[PropertyKeys.TEXT_ALIGN] = align
        if (weight > 0) p[PropertyKeys.WEIGHT] = "$weight"
        return node(ElementTypes.TEXT, w, fixed(h), p)
    }

    private enum class BtnStyle { FILLED, OUTLINE, TEXT, DANGER }

    private fun button(
        value: String,
        w: SizeSpec,
        h: Float = 44f,
        style: BtnStyle = BtnStyle.FILLED,
        weight: Int = 0
    ): UiElement {
        val p = mutableMapOf(PropertyKeys.TEXT to value)
        when (style) {
            BtnStyle.FILLED -> Unit
            BtnStyle.DANGER -> p[PropertyKeys.BG_COLOR] = DANGER
            BtnStyle.OUTLINE -> {
                p[PropertyKeys.BG_COLOR] = PropertyRegistry.TRANSPARENT
                p[PropertyKeys.TEXT_COLOR] = PropertyRegistry.ACCENT
                p[PropertyKeys.BORDER_WIDTH] = "2"
                p[PropertyKeys.BORDER_COLOR] = PropertyRegistry.ACCENT
            }
            BtnStyle.TEXT -> {
                p[PropertyKeys.BG_COLOR] = PropertyRegistry.TRANSPARENT
                p[PropertyKeys.TEXT_COLOR] = PropertyRegistry.ACCENT
            }
        }
        if (weight > 0) p[PropertyKeys.WEIGHT] = "$weight"
        return node(ElementTypes.BUTTON, w, fixed(h), p)
    }

    private fun input(hint: String, w: SizeSpec = fill(), h: Float = 48f, weight: Int = 0): UiElement {
        val p = mutableMapOf(PropertyKeys.HINT to hint)
        if (weight > 0) p[PropertyKeys.WEIGHT] = "$weight"
        return node(ElementTypes.INPUT, w, fixed(h), p)
    }

    private fun image(w: SizeSpec, h: Float, radius: Int = 0, weight: Int = 0): UiElement {
        val p = mutableMapOf<String, String>()
        if (radius > 0) p[PropertyKeys.CORNER_RADIUS] = "$radius"
        if (weight > 0) p[PropertyKeys.WEIGHT] = "$weight"
        return node(ElementTypes.IMAGE, w, fixed(h), p)
    }

    private fun divider() = node(ElementTypes.DIVIDER, fill(), fixed(1f))

    private fun column(
        w: SizeSpec,
        h: Float,
        children: List<UiElement>,
        spacing: Int = 8,
        padding: Int = 0,
        type: String = ElementTypes.CONTAINER,
        extra: Map<String, String> = emptyMap(),
        weight: Int = 0
    ): UiElement {
        val p = plain(type) + pad(padding) + mapOf(PropertyKeys.SPACING to "$spacing") + extra +
            if (weight > 0) mapOf(PropertyKeys.WEIGHT to "$weight") else emptyMap()
        return node(type, w, fixed(h), p, ChildLayout.COLUMN, children)
    }

    private fun row(
        w: SizeSpec,
        h: Float,
        children: List<UiElement>,
        spacing: Int = 8,
        padding: Int = 0,
        type: String = ElementTypes.CONTAINER,
        extra: Map<String, String> = emptyMap(),
        weight: Int = 0
    ): UiElement {
        val p = plain(type) + pad(padding) + mapOf(PropertyKeys.SPACING to "$spacing", PropertyKeys.CHILD_ALIGN_V to Aligns.CENTER) +
            extra + if (weight > 0) mapOf(PropertyKeys.WEIGHT to "$weight") else emptyMap()
        return node(type, w, fixed(h), p, ChildLayout.ROW, children)
    }

    private fun preset(
        id: String,
        label: Int,
        category: PresetCategory,
        build: (text: (Int) -> String) -> UiElement
    ) = ComponentPreset(id, label, category) { t -> listOf(build(t)) }

    // ---- المكوّنات ----

    val presets: List<ComponentPreset> = listOf(

        // تسجيل الدخول
        preset("pro_login_form", R.string.pc_login_form, PresetCategory.LOGIN) { t ->
            column(
                fixed(300f), 330f, padding = 16, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pc_login_title), fill(), 32f, 22, bold = true),
                    input(t(R.string.pc_email)),
                    input(t(R.string.pc_password)),
                    button(t(R.string.pc_login_btn), fill()),
                    button(t(R.string.pc_forgot), fill(), 36f, BtnStyle.TEXT),
                    text(t(R.string.pc_no_account), fill(), 24f, 13, color = MUTED, align = TextAligns.CENTER)
                )
            )
        },
        preset("pro_login_signup", R.string.pc_signup_form, PresetCategory.LOGIN) { t ->
            column(
                fixed(300f), 360f, padding = 16, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pc_signup_title), fill(), 32f, 22, bold = true),
                    input(t(R.string.pc_full_name)),
                    input(t(R.string.pc_email)),
                    input(t(R.string.pc_password)),
                    button(t(R.string.pc_signup_btn), fill())
                )
            )
        },
        preset("pro_login_phone", R.string.pc_phone_login, PresetCategory.LOGIN) { t ->
            column(
                fixed(300f), 230f, padding = 16, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pc_phone_title), fill(), 28f, 20, bold = true),
                    text(t(R.string.pc_phone_hint), fill(), 22f, 13, color = MUTED),
                    input(t(R.string.pc_phone)),
                    button(t(R.string.pc_send_code), fill())
                )
            )
        },
        preset("pro_login_otp", R.string.pc_otp, PresetCategory.LOGIN) { t ->
            column(
                fixed(300f), 220f, padding = 16, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pc_otp_title), fill(), 28f, 20, bold = true, align = TextAligns.CENTER),
                    row(
                        fill(), 56f, spacing = 10,
                        extra = mapOf(PropertyKeys.ARRANGEMENT to Arrangements.CENTER),
                        children = List(4) { input("•", fixed(52f), 52f) }
                    ),
                    button(t(R.string.pc_verify), fill()),
                    button(t(R.string.pc_resend), fill(), 36f, BtnStyle.TEXT)
                )
            )
        },

        // بطاقات المنتجات
        preset("pro_product_card", R.string.pc_product_card, PresetCategory.PRODUCTS) { t ->
            column(
                fixed(180f), 262f, padding = 12, type = ElementTypes.CARD,
                children = listOf(
                    image(fill(), 120f, 8),
                    text(t(R.string.pc_product_name), fill(), 22f, 15, bold = true),
                    text(t(R.string.pc_product_price), fill(), 22f, 15, bold = true, color = PropertyRegistry.ACCENT),
                    button(t(R.string.pc_add_to_cart), fill(), 40f)
                )
            )
        },
        preset("pro_product_row", R.string.pc_product_row, PresetCategory.PRODUCTS) { t ->
            row(
                fill(), 104f, padding = 12, type = ElementTypes.CARD, spacing = 12,
                children = listOf(
                    image(fixed(80f), 80f, 8),
                    column(
                        fixed(100f), 80f, spacing = 6, weight = 1,
                        children = listOf(
                            text(t(R.string.pc_product_name), fill(), 24f, 15, bold = true),
                            text(t(R.string.pc_product_price), fill(), 22f, 14, color = PropertyRegistry.ACCENT)
                        )
                    ),
                    button(t(R.string.pc_add), fixed(72f), 36f)
                )
            )
        },
        preset("pro_product_pair", R.string.pc_product_pair, PresetCategory.PRODUCTS) { t ->
            fun cell() = column(
                fixed(100f), 220f, padding = 10, type = ElementTypes.CARD, weight = 1,
                children = listOf(
                    image(fill(), 100f, 8),
                    text(t(R.string.pc_product_name), fill(), 22f, 14, bold = true),
                    text(t(R.string.pc_product_price), fill(), 22f, 14, color = PropertyRegistry.ACCENT)
                )
            )
            row(fill(), 232f, spacing = 12, padding = 6, children = listOf(cell(), cell()), extra = mapOf(PropertyKeys.CHILD_ALIGN_V to Aligns.START))
        },

        // شريط التنقل والتبويبات
        preset("pro_bottom_nav", R.string.pc_bottom_nav, PresetCategory.NAVIGATION) { t ->
            fun item(label: Int, active: Boolean) = column(
                fixed(60f), 52f, spacing = 2, weight = 1,
                extra = mapOf(PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER),
                children = listOf(
                    image(fixed(24f), 24f, 12),
                    text(
                        t(label), fill(), 16f, 11, bold = active,
                        color = if (active) PropertyRegistry.ACCENT else MUTED, align = TextAligns.CENTER
                    )
                )
            )
            row(
                fill(), 68f, padding = 8, spacing = 0,
                extra = mapOf(PropertyKeys.BG_COLOR to PropertyRegistry.WHITE, PropertyKeys.ARRANGEMENT to Arrangements.EVENLY),
                children = listOf(
                    item(R.string.pc_nav_home, true), item(R.string.pc_nav_search, false),
                    item(R.string.pc_nav_cart, false), item(R.string.pc_nav_profile, false)
                )
            )
        },
        preset("pro_tabs", R.string.pc_tabs, PresetCategory.NAVIGATION) { t ->
            fun tab(label: Int, active: Boolean) = text(
                t(label), fixed(90f), 40f, 14, bold = active,
                color = if (active) PropertyRegistry.ACCENT else MUTED, align = TextAligns.CENTER, weight = 1
            )
            column(
                fill(), 46f, spacing = 0,
                extra = mapOf(PropertyKeys.BG_COLOR to PropertyRegistry.WHITE),
                children = listOf(
                    row(
                        fill(), 42f, spacing = 0,
                        children = listOf(tab(R.string.pc_tab_a, true), tab(R.string.pc_tab_b, false), tab(R.string.pc_tab_c, false))
                    ),
                    node(
                        ElementTypes.DIVIDER, fill(), fixed(3f),
                        mapOf(PropertyKeys.BG_COLOR to PropertyRegistry.ACCENT)
                    )
                )
            )
        },
        preset("pro_appbar", R.string.pc_appbar, PresetCategory.NAVIGATION) { t ->
            row(
                fill(), 56f, padding = 12, spacing = 12,
                extra = mapOf(PropertyKeys.BG_COLOR to PropertyRegistry.ACCENT),
                children = listOf(
                    image(fixed(24f), 24f, 4),
                    text(t(R.string.pc_screen_title), fixed(100f), 28f, 18, bold = true, color = PropertyRegistry.WHITE, weight = 1),
                    image(fixed(24f), 24f, 4)
                )
            )
        },

        // القوائم
        preset("pro_menu_settings", R.string.pc_menu_settings, PresetCategory.MENUS) { t ->
            fun item(label: Int) = row(
                fill(), 48f, spacing = 8,
                children = listOf(
                    image(fixed(24f), 24f, 12),
                    text(t(label), fixed(100f), 24f, 15, weight = 1),
                    text("›", fixed(16f), 24f, 18, color = MUTED)
                )
            )
            column(
                fixed(300f), 214f, padding = 12, spacing = 0, type = ElementTypes.CARD,
                children = listOf(
                    item(R.string.pc_menu_account), divider(),
                    item(R.string.pc_menu_notifications), divider(),
                    item(R.string.pc_menu_language), divider(),
                    item(R.string.pc_menu_about)
                )
            )
        },
        preset("pro_menu_dropdown", R.string.pc_menu_dropdown, PresetCategory.MENUS) { t ->
            fun item(label: Int, danger: Boolean = false) = text(
                t(label), fill(), 40f, 15, color = if (danger) DANGER else null
            )
            column(
                fixed(200f), 172f, padding = 8, spacing = 0, type = ElementTypes.CARD,
                children = listOf(
                    item(R.string.pc_menu_edit), item(R.string.pc_menu_share),
                    item(R.string.pc_menu_delete, true)
                )
            )
        },
        preset("pro_menu_list", R.string.pc_menu_list, PresetCategory.MENUS) { t ->
            node(
                ElementTypes.LIST, fixed(280f), fixed(180f),
                mapOf(PropertyKeys.ITEMS to t(R.string.preset_list_items), PropertyKeys.ITEM_HEIGHT to "44")
            )
        },

        // النوافذ المنبثقة
        preset("pro_dialog_confirm", R.string.pc_dialog_confirm, PresetCategory.DIALOGS) { t ->
            column(
                fixed(280f), 190f, padding = 20, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pc_dialog_title), fill(), 26f, 18, bold = true),
                    text(t(R.string.pc_dialog_body), fill(), 44f, 14, color = MUTED),
                    row(
                        fill(), 44f,
                        extra = mapOf(PropertyKeys.ARRANGEMENT to Arrangements.END),
                        children = listOf(
                            button(t(R.string.preset_layout_btn_b), fixed(90f), 40f, BtnStyle.TEXT),
                            button(t(R.string.pc_confirm), fixed(90f), 40f)
                        )
                    )
                )
            )
        },
        preset("pro_dialog_alert", R.string.pc_dialog_alert, PresetCategory.DIALOGS) { t ->
            column(
                fixed(280f), 200f, padding = 20, type = ElementTypes.CARD,
                extra = mapOf(PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER),
                children = listOf(
                    image(fixed(48f), 48f, 24),
                    text(t(R.string.pc_alert_title), fill(), 26f, 18, bold = true, color = DANGER, align = TextAligns.CENTER),
                    text(t(R.string.pc_alert_body), fill(), 40f, 14, color = MUTED, align = TextAligns.CENTER),
                    button(t(R.string.pc_ok), fill(), 40f, BtnStyle.DANGER)
                )
            )
        },
        preset("pro_bottom_sheet", R.string.pc_bottom_sheet, PresetCategory.DIALOGS) { t ->
            fun item(label: Int) = row(
                fill(), 44f,
                children = listOf(image(fixed(24f), 24f, 12), text(t(label), fixed(100f), 24f, 15, weight = 1))
            )
            column(
                fill(), 232f, padding = 16, type = ElementTypes.CARD,
                extra = mapOf(PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER),
                children = listOf(
                    node(
                        ElementTypes.CONTAINER, fixed(40f), fixed(4f),
                        mapOf(PropertyKeys.BG_COLOR to PropertyRegistry.LINE, PropertyKeys.CORNER_RADIUS to "2")
                    ),
                    text(t(R.string.pc_sheet_title), fill(), 26f, 17, bold = true),
                    item(R.string.pc_menu_share), item(R.string.pc_menu_edit), item(R.string.pc_menu_delete)
                )
            )
        },

        // بطاقات الأخبار
        preset("pro_news_card", R.string.pc_news_card, PresetCategory.NEWS) { t ->
            column(
                fixed(300f), 308f, padding = 0, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    image(fill(), 150f),
                    column(
                        fill(), 140f, padding = 12, spacing = 6,
                        children = listOf(
                            text(t(R.string.pc_news_tag), fill(), 18f, 12, bold = true, color = PropertyRegistry.ACCENT),
                            text(t(R.string.pc_news_title), fill(), 44f, 17, bold = true),
                            text(t(R.string.pc_news_date), fill(), 18f, 12, color = MUTED)
                        )
                    )
                )
            )
        },
        preset("pro_news_row", R.string.pc_news_row, PresetCategory.NEWS) { t ->
            row(
                fill(), 112f, padding = 10, spacing = 12, type = ElementTypes.CARD,
                children = listOf(
                    image(fixed(92f), 92f, 8),
                    column(
                        fixed(100f), 92f, spacing = 6, weight = 1,
                        children = listOf(
                            text(t(R.string.pc_news_title), fill(), 44f, 15, bold = true),
                            text(t(R.string.pc_news_date), fill(), 18f, 12, color = MUTED)
                        )
                    )
                )
            )
        },

        // صفوف المحادثات
        preset("pro_chat_row", R.string.pc_chat_row, PresetCategory.CHAT) { t ->
            row(
                fill(), 72f, padding = 12, spacing = 12,
                children = listOf(
                    image(fixed(48f), 48f, 24),
                    column(
                        fixed(100f), 46f, spacing = 4, weight = 1,
                        children = listOf(
                            text(t(R.string.pc_chat_name), fill(), 22f, 16, bold = true),
                            text(t(R.string.pc_chat_last), fill(), 20f, 13, color = MUTED)
                        )
                    ),
                    text(t(R.string.pc_chat_time), fixed(52f), 18f, 12, color = MUTED, align = TextAligns.END)
                )
            )
        },
        preset("pro_chat_in", R.string.pc_chat_in, PresetCategory.CHAT) { t ->
            node(
                ElementTypes.CONTAINER, fixed(220f), fixed(48f),
                pad(12) + mapOf(PropertyKeys.BG_COLOR to SOFT, PropertyKeys.CORNER_RADIUS to "16"),
                ChildLayout.BOX,
                listOf(text(t(R.string.pc_chat_msg_in), fill(), 22f, 14))
            )
        },
        preset("pro_chat_out", R.string.pc_chat_out, PresetCategory.CHAT) { t ->
            node(
                ElementTypes.CONTAINER, fixed(220f), fixed(48f),
                pad(12) + mapOf(PropertyKeys.BG_COLOR to PropertyRegistry.ACCENT, PropertyKeys.CORNER_RADIUS to "16"),
                ChildLayout.BOX,
                listOf(text(t(R.string.pc_chat_msg_out), fill(), 22f, 14, color = PropertyRegistry.WHITE))
            )
        },
        preset("pro_chat_input", R.string.pc_chat_input, PresetCategory.CHAT) { t ->
            row(
                fill(), 64f, padding = 8, spacing = 8,
                extra = mapOf(PropertyKeys.BG_COLOR to PropertyRegistry.WHITE),
                children = listOf(
                    input(t(R.string.pc_chat_type), fixed(100f), 44f, weight = 1),
                    button(t(R.string.pc_send), fixed(72f), 44f)
                )
            )
        }
    )
}
