package com.arabicuieditor.data.model

import com.arabicuieditor.R

/**
 * مكتبة المكوّنات الاحترافية: مكوّنات مركّبة جاهزة (تسجيل الدخول، المنتجات، التنقل، القوائم،
 * النوافذ المنبثقة، الأخبار، المحادثات). تُبنى كلها من العناصر والحاويات المرنة والخصائص المسجّلة
 * في [PropertyRegistry]، فكل جزء منها قابل للتحديد والتعديل بعد الإضافة.
 */
internal object ProComponents {

    private val MUTED = Tokens.color(Tokens.MUTED)
    private val DANGER = Tokens.color(Tokens.DANGER)
    private val SOFT = Tokens.color(Tokens.BORDER)
    private val PRIMARY = Tokens.color(Tokens.PRIMARY)
    private val ON_PRIMARY = Tokens.color(Tokens.ON_PRIMARY)
    private val SURFACE = Tokens.color(Tokens.SURFACE)

    // ---- أدوات بناء ----

    private fun fixed(v: Float) = SizeSpec.fixed(v)
    private fun fill() = SizeSpec(SizeMode.MATCH_PARENT, 200f)

    /** يربط نقر عنصر بخطوات «المنطق» الجاهزة. */
    private fun steps(e: UiElement, vararg list: com.arabicuieditor.data.script.Step): UiElement =
        Interactions.withClickAction(e, ClickAction.Blocks(list.toList()))

    private fun toast(msg: String) = com.arabicuieditor.data.script.Step(com.arabicuieditor.data.script.Steps.TOAST, "", msg)
    private fun inc(v: String) = com.arabicuieditor.data.script.Step(com.arabicuieditor.data.script.Steps.INC, v, "1")

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
                p[PropertyKeys.TEXT_COLOR] = PRIMARY
                p[PropertyKeys.BORDER_WIDTH] = "2"
                p[PropertyKeys.BORDER_COLOR] = PRIMARY
            }
            BtnStyle.TEXT -> {
                p[PropertyKeys.BG_COLOR] = PropertyRegistry.TRANSPARENT
                p[PropertyKeys.TEXT_COLOR] = PRIMARY
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

    private fun image(
        w: SizeSpec, h: Float, radius: Int = 0, weight: Int = 0,
        icon: String? = null, iconColor: String? = null
    ): UiElement {
        val p = mutableMapOf<String, String>()
        if (icon != null) {
            p[PropertyKeys.ICON] = icon
            p[PropertyKeys.BG_COLOR] = PropertyRegistry.TRANSPARENT
            if (iconColor != null) p[PropertyKeys.ICON_COLOR] = iconColor
        }
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
                    text(t(R.string.pc_product_price), fill(), 22f, 15, bold = true, color = PRIMARY),
                    steps(button(t(R.string.pc_add_to_cart), fill(), 40f), inc("cart"), toast(t(R.string.pm_toast_cart)))
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
                            text(t(R.string.pc_product_price), fill(), 22f, 14, color = PRIMARY)
                        )
                    ),
                    steps(button(t(R.string.pc_add), fixed(72f), 36f), inc("cart"), toast(t(R.string.pm_toast_cart)))
                )
            )
        },
        preset("pro_product_pair", R.string.pc_product_pair, PresetCategory.PRODUCTS) { t ->
            fun cell() = column(
                fixed(100f), 220f, padding = 10, type = ElementTypes.CARD, weight = 1,
                children = listOf(
                    image(fill(), 100f, 8),
                    text(t(R.string.pc_product_name), fill(), 22f, 14, bold = true),
                    text(t(R.string.pc_product_price), fill(), 22f, 14, color = PRIMARY)
                )
            )
            row(fill(), 232f, spacing = 12, padding = 6, children = listOf(cell(), cell()), extra = mapOf(PropertyKeys.CHILD_ALIGN_V to Aligns.START))
        },

        preset("pro_food_card", R.string.pc_food_card, PresetCategory.PRODUCTS) { t ->
            column(
                fill(), 268f, padding = 10, type = ElementTypes.CARD, spacing = 8,
                children = listOf(
                    image(fill(), 140f, 10),
                    text(t(R.string.pc_food_name), fill(), 24f, 16, bold = true),
                    text(t(R.string.pc_food_desc), fill(), 20f, 13, color = MUTED),
                    row(
                        fill(), 40f, spacing = 8,
                        children = listOf(
                            text(t(R.string.pc_food_price), fixed(100f), 24f, 15, bold = true, color = PRIMARY, weight = 1),
                            steps(button(t(R.string.pc_food_add), fixed(112f), 36f), inc("cart"), toast(t(R.string.pm_toast_cart)))
                        )
                    )
                )
            )
        },

        preset("pro_property_card", R.string.pc_property_card, PresetCategory.CARDS) { t ->
            column(
                fill(), 282f, padding = 10, type = ElementTypes.CARD, spacing = 8,
                children = listOf(
                    image(fill(), 150f, 10),
                    row(
                        fill(), 28f, spacing = 8,
                        children = listOf(
                            text(t(R.string.pc_property_price), fixed(100f), 26f, 17, bold = true, color = PRIMARY, weight = 1),
                            text(t(R.string.pc_property_kind), fixed(70f), 22f, 13, color = MUTED)
                        )
                    ),
                    text(t(R.string.pc_property_title), fill(), 24f, 15, bold = true),
                    text(t(R.string.pc_property_place), fill(), 20f, 13, color = MUTED),
                    text(t(R.string.pc_property_specs), fill(), 20f, 13, color = MUTED)
                )
            )
        },

        // قوائم الحجز والحساب
        preset("pro_slot_row", R.string.pc_slot_row, PresetCategory.LISTS) { t ->
            row(
                fill(), 76f, padding = 12, type = ElementTypes.CARD, spacing = 12,
                children = listOf(
                    column(
                        fixed(100f), 48f, spacing = 4, weight = 1,
                        children = listOf(
                            text(t(R.string.pc_slot_time), fill(), 24f, 16, bold = true),
                            text(t(R.string.pc_slot_day), fill(), 20f, 13, color = MUTED)
                        )
                    ),
                    steps(button(t(R.string.pc_slot_book), fixed(84f), 36f), toast(t(R.string.pm_toast_booked)))
                )
            )
        },
        preset("pro_profile_header", R.string.pc_profile_header, PresetCategory.CARDS) { t ->
            column(
                fill(), 220f, padding = 16, type = ElementTypes.CARD, spacing = 8,
                extra = mapOf(PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER),
                children = listOf(
                    image(fixed(80f), 80f, 40, icon = "Person", iconColor = PRIMARY),
                    text(t(R.string.pc_profile_name), fill(), 26f, 18, bold = true, align = TextAligns.CENTER),
                    text(t(R.string.pc_profile_email), fill(), 20f, 13, color = MUTED, align = TextAligns.CENTER),
                    button(t(R.string.pc_profile_edit), fixed(160f), 36f, BtnStyle.OUTLINE)
                )
            )
        },

        // شريط التنقل والتبويبات
        preset("pro_bottom_nav", R.string.pc_bottom_nav, PresetCategory.NAVIGATION) { t ->
            fun item(label: Int, active: Boolean, icon: String) = column(
                fixed(60f), 52f, spacing = 2, weight = 1,
                extra = mapOf(PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER),
                children = listOf(
                    image(fixed(24f), 24f, 12, icon = icon, iconColor = if (active) PRIMARY else MUTED),
                    text(
                        t(label), fill(), 16f, 11, bold = active,
                        color = if (active) PRIMARY else MUTED, align = TextAligns.CENTER
                    )
                )
            )
            row(
                fill(), 68f, padding = 8, spacing = 0,
                extra = mapOf(PropertyKeys.BG_COLOR to SURFACE, PropertyKeys.ARRANGEMENT to Arrangements.EVENLY),
                children = listOf(
                    item(R.string.pc_nav_home, true, "Home"), item(R.string.pc_nav_search, false, "Search"),
                    item(R.string.pc_nav_cart, false, "ShoppingCart"), item(R.string.pc_nav_profile, false, "Person")
                )
            )
        },
        preset("pro_tabs", R.string.pc_tabs, PresetCategory.NAVIGATION) { t ->
            fun tab(label: Int, active: Boolean) = text(
                t(label), fixed(90f), 40f, 14, bold = active,
                color = if (active) PRIMARY else MUTED, align = TextAligns.CENTER, weight = 1
            )
            column(
                fill(), 46f, spacing = 0,
                extra = mapOf(PropertyKeys.BG_COLOR to SURFACE),
                children = listOf(
                    row(
                        fill(), 42f, spacing = 0,
                        children = listOf(tab(R.string.pc_tab_a, true), tab(R.string.pc_tab_b, false), tab(R.string.pc_tab_c, false))
                    ),
                    node(
                        ElementTypes.DIVIDER, fill(), fixed(3f),
                        mapOf(PropertyKeys.BG_COLOR to PRIMARY)
                    )
                )
            )
        },
        preset("pro_appbar", R.string.pc_appbar, PresetCategory.NAVIGATION) { t ->
            row(
                fill(), 56f, padding = 12, spacing = 12,
                extra = mapOf(PropertyKeys.BG_COLOR to PRIMARY),
                children = listOf(
                    image(fixed(24f), 24f, 4, icon = "Menu", iconColor = ON_PRIMARY),
                    text(t(R.string.pc_screen_title), fixed(100f), 28f, 18, bold = true, color = ON_PRIMARY, weight = 1),
                    image(fixed(24f), 24f, 4, icon = "Search", iconColor = ON_PRIMARY)
                )
            )
        },

        // القوائم
        preset("pro_menu_settings", R.string.pc_menu_settings, PresetCategory.MENUS) { t ->
            fun item(label: Int) = row(
                fill(), 48f, spacing = 8,
                children = listOf(
                    image(fixed(24f), 24f, 12, icon = "Settings"),
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
                    image(fixed(48f), 48f, 24, icon = "Warning", iconColor = DANGER),
                    text(t(R.string.pc_alert_title), fill(), 26f, 18, bold = true, color = DANGER, align = TextAligns.CENTER),
                    text(t(R.string.pc_alert_body), fill(), 40f, 14, color = MUTED, align = TextAligns.CENTER),
                    button(t(R.string.pc_ok), fill(), 40f, BtnStyle.DANGER)
                )
            )
        },
        preset("pro_bottom_sheet", R.string.pc_bottom_sheet, PresetCategory.DIALOGS) { t ->
            fun item(label: Int) = row(
                fill(), 44f,
                children = listOf(image(fixed(24f), 24f, 12, icon = "Share"), text(t(label), fixed(100f), 24f, 15, weight = 1))
            )
            column(
                fill(), 232f, padding = 16, type = ElementTypes.CARD,
                extra = mapOf(PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER),
                children = listOf(
                    node(
                        ElementTypes.CONTAINER, fixed(40f), fixed(4f),
                        mapOf(PropertyKeys.BG_COLOR to SOFT, PropertyKeys.CORNER_RADIUS to "2")
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
                            text(t(R.string.pc_news_tag), fill(), 18f, 12, bold = true, color = PRIMARY),
                            text(t(R.string.pc_news_title), fill(), 44f, 17, bold = true),
                            text(t(R.string.pc_news_date), fill(), 18f, 12, color = MUTED)
                        )
                    )
                )
            )
        },
        // خبر عاجل: زر «المزيد» يُظهر نص التفاصيل ويخفي نفسه.
        preset("pro_news_more", R.string.pc_news_more, PresetCategory.NEWS) { t ->
            val detail = text(t(R.string.pc_news_more_text), fill(), 60f, 14).let {
                it.copy(properties = it.properties + (PropertyKeys.NAME to "more_text") + (PropertyKeys.VISIBLE to "false"))
            }
            val more = Interactions.withClickAction(
                button(t(R.string.pc_news_more_btn), fixed(110f), 40f, BtnStyle.TEXT).let {
                    it.copy(properties = it.properties + (PropertyKeys.NAME to "more_btn"))
                },
                ClickAction.Blocks(listOf(
                    com.arabicuieditor.data.script.Step(com.arabicuieditor.data.script.Steps.SHOW, "more_text"),
                    com.arabicuieditor.data.script.Step(com.arabicuieditor.data.script.Steps.HIDE, "more_btn")
                ))
            )
            column(
                fill(), 150f, padding = 12, spacing = 6, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pc_news_tag), fill(), 18f, 12, bold = true, color = PRIMARY),
                    text(t(R.string.pc_news_title), fill(), 44f, 17, bold = true),
                    detail,
                    more
                )
            ).let { it.copy(layout = it.layout.copy(height = SizeSpec(SizeMode.WRAP_CONTENT, 150f))) }
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
                pad(12) + mapOf(PropertyKeys.BG_COLOR to PRIMARY, PropertyKeys.CORNER_RADIUS to "16"),
                ChildLayout.BOX,
                listOf(text(t(R.string.pc_chat_msg_out), fill(), 22f, 14, color = ON_PRIMARY))
            )
        },
        preset("pro_chat_input", R.string.pc_chat_input, PresetCategory.CHAT) { t ->
            row(
                fill(), 64f, padding = 8, spacing = 8,
                extra = mapOf(PropertyKeys.BG_COLOR to SURFACE),
                children = listOf(
                    input(t(R.string.pc_chat_type), fixed(100f), 44f, weight = 1),
                    button(t(R.string.pc_send), fixed(72f), 44f)
                )
            )
        },

        // باحث المنشورات: نموذج البحث (الأزرار أبناء مباشرة ليُربط أولها بالنتائج والأخير بالإعدادات)
        preset("pro_search_form", R.string.pc_search_form, PresetCategory.INPUTS) { t ->
            column(
                fill(), 360f, padding = 14, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pc_sf_title), fill(), 26f, 17, bold = true),
                    input(t(R.string.pc_sf_query), fill(), 48f),
                    input(t(R.string.pc_sf_count), fill(), 48f),
                    input(t(R.string.pc_sf_scope), fill(), 48f),
                    button(t(R.string.pc_sf_start), fill(), 44f),
                    button(t(R.string.pc_sf_stop), fill(), 44f, BtnStyle.DANGER),
                    text(t(R.string.pc_sf_progress), fill(), 22f, 13, color = MUTED),
                    button(t(R.string.pc_sf_settings), fill(), 40f, BtnStyle.TEXT)
                )
            )
        },
        // بطاقة منشور محفوظ/مطابق: الأزرار أبناء مباشرة (4 نسخ، 5 تعديل)
        preset("pro_post_card", R.string.pc_post_card, PresetCategory.CARDS) { t ->
            column(
                fill(), 300f, padding = 12, spacing = 6, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pc_post_title), fill(), 26f, 16, bold = true),
                    text(t(R.string.pc_post_body), fill(), 60f, 14),
                    text(t(R.string.pc_post_meta), fill(), 20f, 12, color = MUTED),
                    text(t(R.string.pc_post_link), fill(), 20f, 12, color = PRIMARY),
                    steps(button(t(R.string.pc_post_copy), fill(), 40f), toast(t(R.string.pm_toast_copied))),
                    button(t(R.string.pc_post_edit), fill(), 40f, BtnStyle.OUTLINE)
                )
            )
        },
        // أدوات قائمة المنشورات: بحث داخلي + نسخ المحدد + فتح المكتبة (الأزرار أبناء مباشرة)
        preset("pro_post_tools", R.string.pc_post_tools, PresetCategory.INPUTS) { t ->
            column(
                fill(), 200f, padding = 12, spacing = 8,
                children = listOf(
                    input(t(R.string.pc_pt_search), fill(), 48f),
                    steps(button(t(R.string.pc_pt_copy_selected), fill(), 40f), toast(t(R.string.pm_toast_copied))),
                    button(t(R.string.pc_pt_select_all), fill(), 40f, BtnStyle.OUTLINE),
                    button(t(R.string.pc_pt_library), fill(), 40f, BtnStyle.TEXT)
                )
            )
        },
        preset("pro_export_bar", R.string.pc_export_bar, PresetCategory.BUTTONS) { t ->
            row(
                fill(), 56f, padding = 8, spacing = 8,
                children = listOf(
                    button(t(R.string.pc_export_json), fixed(100f), 44f, weight = 1),
                    button(t(R.string.pc_export_excel), fixed(100f), 44f, BtnStyle.OUTLINE, weight = 1)
                )
            )
        },
        preset("pro_search_settings", R.string.pc_search_settings, PresetCategory.INPUTS) { t ->
            column(
                fill(), 330f, padding = 14, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pc_ss_title), fill(), 26f, 17, bold = true),
                    input(t(R.string.pc_ss_phrase), fill(), 48f),
                    input(t(R.string.pc_ss_must), fill(), 48f),
                    input(t(R.string.pc_ss_exclude), fill(), 48f),
                    input(t(R.string.pc_ss_period), fill(), 48f),
                    input(t(R.string.pc_ss_pages), fill(), 48f),
                    button(t(R.string.pc_ss_save), fill(), 44f)
                )
            )
        },
        preset("pro_post_edit", R.string.pc_post_edit_form, PresetCategory.INPUTS) { t ->
            column(
                fill(), 330f, padding = 14, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    input(t(R.string.pc_pe_title), fill(), 48f),
                    input(t(R.string.pc_pe_body), fill(), 140f),
                    steps(button(t(R.string.pc_pe_save), fill(), 44f), toast(t(R.string.pm_toast_saved))),
                    button(t(R.string.pc_pe_delete), fill(), 44f, BtnStyle.DANGER)
                )
            )
        },
        // سوقنا: شريط تنقل (0 الرئيسية، 1 بحث، 2 إضافة، 3 رسائل، 4 حسابي)
        preset("pro_market_nav", R.string.pm_market_nav, PresetCategory.NAVIGATION) { t ->
            fun item(label: Int, active: Boolean, icon: String) = column(
                fixed(56f), 52f, spacing = 2, weight = 1,
                extra = mapOf(PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER),
                children = listOf(
                    image(fixed(24f), 24f, 12, icon = icon, iconColor = if (active) PRIMARY else MUTED),
                    text(t(label), fill(), 16f, 11, bold = active, color = if (active) PRIMARY else MUTED, align = TextAligns.CENTER)
                )
            )
            row(
                fill(), 68f, padding = 8, spacing = 0,
                extra = mapOf(PropertyKeys.BG_COLOR to SURFACE, PropertyKeys.ARRANGEMENT to Arrangements.EVENLY),
                children = listOf(
                    item(R.string.pm_nav_home, true, "Home"), item(R.string.pm_nav_search, false, "Search"),
                    item(R.string.pm_nav_add, false, "Add"), item(R.string.pm_nav_msgs, false, "Email"),
                    item(R.string.pm_nav_me, false, "Person")
                )
            )
        },
        preset("pro_ad_search", R.string.pm_ad_search, PresetCategory.INPUTS) { t ->
            row(
                fill(), 64f, padding = 8, spacing = 8,
                children = listOf(
                    input(t(R.string.pm_search_hint), fixed(100f), 44f, weight = 1),
                    button(t(R.string.pm_search_btn), fixed(72f), 44f)
                )
            )
        },
        preset("pro_category_list", R.string.pm_category_list, PresetCategory.LISTS) { t ->
            column(
                fill(), 260f, padding = 12, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pm_cat_title), fill(), 24f, 16, bold = true),
                    node(ElementTypes.LIST, fill(), fixed(200f), mapOf(PropertyKeys.ITEMS to t(R.string.pm_cat_items), PropertyKeys.ITEM_HEIGHT to "40"))
                )
            )
        },
        preset("pro_ad_row", R.string.pm_ad_row, PresetCategory.PRODUCTS) { t ->
            row(
                fill(), 104f, padding = 12, type = ElementTypes.CARD, spacing = 12,
                children = listOf(
                    image(fixed(80f), 80f, 8),
                    column(
                        fixed(100f), 80f, spacing = 6, weight = 1,
                        children = listOf(
                            text(t(R.string.pm_ad_title), fill(), 24f, 15, bold = true),
                            text(t(R.string.pm_ad_price), fill(), 22f, 14, bold = true, color = PRIMARY),
                            text(t(R.string.pm_ad_loc), fill(), 18f, 11, color = MUTED)
                        )
                    ),
                    steps(button(t(R.string.pm_ad_save), fixed(64f), 36f, BtnStyle.OUTLINE), toast(t(R.string.pm_toast_fav)))
                )
            )
        },
        // نموذج التصفية: 8 تطبيق، 9 حفظ البحث
        preset("pro_ad_filter", R.string.pm_ad_filter, PresetCategory.INPUTS) { t ->
            column(
                fill(), 550f, padding = 14, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pm_f_title), fill(), 26f, 17, bold = true),
                    input(t(R.string.pm_f_city), fill(), 48f), input(t(R.string.pm_f_region), fill(), 48f),
                    input(t(R.string.pm_f_category), fill(), 48f), input(t(R.string.pm_f_min), fill(), 48f),
                    input(t(R.string.pm_f_max), fill(), 48f), input(t(R.string.pm_f_condition), fill(), 48f),
                    input(t(R.string.pm_f_sort), fill(), 48f),
                    steps(button(t(R.string.pm_f_apply), fill(), 44f), toast(t(R.string.pm_toast_filter))),
                    steps(button(t(R.string.pm_f_save), fill(), 40f, BtnStyle.OUTLINE), toast(t(R.string.pm_toast_saved_search)))
                )
            )
        },
        // تفاصيل الإعلان: 6 اتصال، 7 واتساب، 8 مراسلة، 9 مفضلة، 10 مشاركة، 11 إبلاغ
        preset("pro_ad_details", R.string.pm_ad_details, PresetCategory.CARDS) { t ->
            column(
                fill(), 700f, padding = 14, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    image(fill(), 180f, 8),
                    text(t(R.string.pm_d_title), fill(), 26f, 17, bold = true),
                    text(t(R.string.pm_d_price), fill(), 24f, 17, bold = true, color = PRIMARY),
                    text(t(R.string.pm_d_desc), fill(), 70f, 14),
                    text(t(R.string.pm_d_meta), fill(), 20f, 12, color = MUTED),
                    text(t(R.string.pm_d_adv), fill(), 20f, 13, bold = true),
                    button(t(R.string.pm_d_call), fill(), 40f),
                    button(t(R.string.pm_d_whatsapp), fill(), 40f),
                    button(t(R.string.pm_d_msg), fill(), 40f, BtnStyle.OUTLINE),
                    steps(button(t(R.string.pm_d_fav), fill(), 40f, BtnStyle.OUTLINE), toast(t(R.string.pm_toast_fav))),
                    button(t(R.string.pm_d_share), fill(), 40f, BtnStyle.OUTLINE),
                    button(t(R.string.pm_d_report), fill(), 40f, BtnStyle.TEXT)
                )
            )
        },
        // نموذج إضافة إعلان: 8 رفع الصور، 9 معاينة، 10 نشر
        preset("pro_ad_form", R.string.pm_ad_form, PresetCategory.INPUTS) { t ->
            column(
                fill(), 650f, padding = 14, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pm_a_title), fill(), 26f, 17, bold = true),
                    input(t(R.string.pm_a_category), fill(), 48f), input(t(R.string.pm_a_name), fill(), 48f),
                    input(t(R.string.pm_a_desc), fill(), 100f), input(t(R.string.pm_a_price), fill(), 48f),
                    input(t(R.string.pm_a_city), fill(), 48f), input(t(R.string.pm_a_phone), fill(), 48f),
                    input(t(R.string.pm_a_condition), fill(), 48f),
                    button(t(R.string.pm_a_photos), fill(), 40f, BtnStyle.OUTLINE),
                    button(t(R.string.pm_a_preview), fill(), 40f, BtnStyle.OUTLINE),
                    button(t(R.string.pm_a_publish), fill(), 44f)
                )
            )
        },
        preset("pro_account_menu", R.string.pm_account_menu, PresetCategory.MENUS) { t ->
            node(
                ElementTypes.LIST, fill(), fixed(264f),
                mapOf(PropertyKeys.ITEMS to t(R.string.pm_acc_items), PropertyKeys.ITEM_HEIGHT to "44")
            )
        },
        // الإبلاغ: 3 إرسال
        preset("pro_report_form", R.string.pm_report_form, PresetCategory.INPUTS) { t ->
            column(
                fill(), 270f, padding = 14, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pm_r_title), fill(), 26f, 17, bold = true),
                    input(t(R.string.pm_r_reason), fill(), 48f), input(t(R.string.pm_r_details), fill(), 100f),
                    button(t(R.string.pm_r_send), fill(), 44f, BtnStyle.DANGER)
                )
            )
        },
        // لوحة الإدارة
        preset("pro_admin_stats", R.string.pm_admin_stats, PresetCategory.CARDS) { t ->
            fun stat(label: Int) = column(
                fixed(90f), 60f, spacing = 2, weight = 1,
                extra = mapOf(PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER),
                children = listOf(
                    text(t(R.string.pm_s_zero), fill(), 30f, 22, bold = true, color = PRIMARY, align = TextAligns.CENTER),
                    text(t(label), fill(), 18f, 12, color = MUTED, align = TextAligns.CENTER)
                )
            )
            column(
                fill(), 120f, padding = 12, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pm_s_title), fill(), 26f, 16, bold = true),
                    row(fill(), 60f, spacing = 8, children = listOf(stat(R.string.pm_s_users), stat(R.string.pm_s_ads), stat(R.string.pm_s_reports)))
                )
            )
        },
        preset("pro_admin_ad_row", R.string.pm_admin_ad_row, PresetCategory.CARDS) { t ->
            column(
                fill(), 130f, padding = 12, spacing = 8, type = ElementTypes.CARD,
                extra = mapOf(PropertyKeys.NAME to "adm_card"),
                children = listOf(
                    text(t(R.string.pm_x_title), fill(), 22f, 15, bold = true),
                    text(t(R.string.pm_x_meta), fill(), 20f, 12, color = MUTED),
                    row(
                        fill(), 44f, spacing = 8,
                        children = listOf(
                            steps(button(t(R.string.pm_x_approve), fixed(100f), 44f, weight = 1), toast(t(R.string.pm_toast_approved)), com.arabicuieditor.data.script.Step(com.arabicuieditor.data.script.Steps.HIDE, "adm_card")),
                            steps(button(t(R.string.pm_x_reject), fixed(100f), 44f, BtnStyle.DANGER, weight = 1), toast(t(R.string.pm_toast_rejected)), com.arabicuieditor.data.script.Step(com.arabicuieditor.data.script.Steps.HIDE, "adm_card"))
                        )
                    )
                )
            )
        },
        preset("pro_admin_menu", R.string.pm_admin_menu, PresetCategory.MENUS) { t ->
            node(
                ElementTypes.LIST, fill(), fixed(264f),
                mapOf(PropertyKeys.ITEMS to t(R.string.pm_admin_items), PropertyKeys.ITEM_HEIGHT to "44")
            )
        },
        // مكوّنات ناقصة اكتُشفت من القوالب: خريطة/موقع، تقييم، شرائح، معرض، تقدّم
        // بطاقة الخريطة: 3 فتح في الخرائط، 4 الاتجاهات (مكان الخريطة صورة قابلة للاستبدال)
        preset("pro_map_card", R.string.pg_map_card, PresetCategory.CARDS) { t ->
            column(
                fill(), 310f, padding = 12, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    column(
                        fill(), 160f, spacing = 6,
                        extra = mapOf(PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER, PropertyKeys.BG_COLOR to SURFACE, PropertyKeys.CORNER_RADIUS to "10"),
                        children = listOf(
                            image(fixed(48f), 48f, 24, icon = "LocationOn", iconColor = PRIMARY),
                            text(t(R.string.pg_map_label), fill(), 20f, 12, color = MUTED, align = TextAligns.CENTER)
                        )
                    ),
                    text(t(R.string.pg_map_address), fill(), 22f, 14),
                    button(t(R.string.pg_map_open), fill(), 40f),
                    button(t(R.string.pg_map_dir), fill(), 40f, BtnStyle.OUTLINE)
                )
            )
        },
        preset("pro_location_row", R.string.pg_location_row, PresetCategory.CARDS) { t ->
            row(
                fill(), 72f, padding = 12, spacing = 12, type = ElementTypes.CARD,
                children = listOf(
                    image(fixed(32f), 32f, 16, icon = "LocationOn", iconColor = PRIMARY),
                    column(
                        fixed(100f), 44f, spacing = 2, weight = 1,
                        children = listOf(
                            text(t(R.string.pg_loc_title), fill(), 22f, 15, bold = true),
                            text(t(R.string.pg_loc_sub), fill(), 18f, 12, color = MUTED)
                        )
                    ),
                    button(t(R.string.pg_loc_change), fixed(72f), 36f, BtnStyle.OUTLINE)
                )
            )
        },
        preset("pro_rating_row", R.string.pg_rating_row, PresetCategory.CARDS) { t ->
            row(
                fill(), 48f, padding = 12, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pg_rate_stars), fixed(100f), 24f, 18, color = "#FFD18A27"),
                    text(t(R.string.pg_rate_text), fixed(100f), 24f, 13, color = MUTED, weight = 1)
                )
            )
        },
        preset("pro_filter_chips", R.string.pg_filter_chips, PresetCategory.BUTTONS) { t ->
            row(
                fill(), 48f, padding = 8, spacing = 8,
                children = listOf(
                    button(t(R.string.pg_chip_a), fixed(80f), 32f, BtnStyle.OUTLINE, weight = 1),
                    button(t(R.string.pg_chip_b), fixed(80f), 32f, BtnStyle.OUTLINE, weight = 1),
                    button(t(R.string.pg_chip_c), fixed(80f), 32f, BtnStyle.OUTLINE, weight = 1)
                )
            )
        },
        preset("pro_gallery_strip", R.string.pg_gallery, PresetCategory.IMAGES) { _ ->
            row(
                fill(), 96f, padding = 8, spacing = 8,
                children = listOf(
                    image(fixed(90f), 80f, 8, weight = 1), image(fixed(90f), 80f, 8, weight = 1), image(fixed(90f), 80f, 8, weight = 1)
                )
            )
        },
        // شريط تقدم ثابت الشكل (النسبة الظاهرة 75٪؛ تُعدَّل بنسبة الوزن في الخصائص)
        preset("pro_progress_bar", R.string.pg_progress, PresetCategory.CARDS) { t ->
            column(
                fill(), 72f, padding = 12, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    row(
                        fill(), 22f, spacing = 8,
                        children = listOf(
                            text(t(R.string.pg_prog_label), fixed(100f), 22f, 14, bold = true, weight = 1),
                            text(t(R.string.pg_prog_value), fixed(60f), 22f, 14, color = PRIMARY, align = TextAligns.END)
                        )
                    ),
                    row(
                        fill(), 10f, spacing = 0,
                        children = listOf(
                            node(ElementTypes.CONTAINER, fixed(100f), fixed(10f), mapOf(PropertyKeys.BG_COLOR to PRIMARY, PropertyKeys.CORNER_RADIUS to "5", PropertyKeys.WEIGHT to "3")),
                            node(ElementTypes.CONTAINER, fixed(100f), fixed(10f), mapOf(PropertyKeys.BG_COLOR to SURFACE, PropertyKeys.CORNER_RADIUS to "5", PropertyKeys.WEIGHT to "1"))
                        )
                    )
                )
            )
        },
        // شريط تمرير (Slider) بصري: المقبض والمسار بالنسب؛ غير تفاعلي (تُعدَّل القيمة بوزن المسار في الخصائص)
        preset("pro_slider_row", R.string.pg_slider, PresetCategory.INPUTS) { t ->
            column(
                fill(), 76f, padding = 12, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    row(
                        fill(), 22f, spacing = 8,
                        children = listOf(
                            text(t(R.string.pg_slider_label), fixed(100f), 22f, 14, bold = true, weight = 1),
                            text(t(R.string.pg_slider_value), fixed(60f), 22f, 14, color = PRIMARY, align = TextAligns.END)
                        )
                    ),
                    row(
                        fill(), 22f, spacing = 0,
                        children = listOf(
                            node(ElementTypes.CONTAINER, fixed(100f), fixed(6f), mapOf(PropertyKeys.BG_COLOR to PRIMARY, PropertyKeys.CORNER_RADIUS to "3", PropertyKeys.WEIGHT to "3")),
                            node(ElementTypes.CONTAINER, fixed(22f), fixed(22f), mapOf(PropertyKeys.BG_COLOR to PRIMARY, PropertyKeys.CORNER_RADIUS to "11", PropertyKeys.BORDER_COLOR to Tokens.color(Tokens.ON_PRIMARY), PropertyKeys.BORDER_WIDTH to "3")),
                            node(ElementTypes.CONTAINER, fixed(100f), fixed(6f), mapOf(PropertyKeys.BG_COLOR to SURFACE, PropertyKeys.CORNER_RADIUS to "3", PropertyKeys.WEIGHT to "2"))
                        )
                    )
                )
            )
        },
        // عناصر تحكم تعمل بخطوات «المنطق» الموجودة (المعاينة وتصدير Compose): خانة، مفتاح، قائمة منسدلة
        preset("pro_checkbox_row", R.string.pc_ctl_checkbox, PresetCategory.INPUTS) { t ->
            val steps = ClickAction.Blocks(listOf(com.arabicuieditor.data.script.Step(com.arabicuieditor.data.script.Steps.TOGGLE, "chk_mark")))
            val mark = text("✓", fill(), 28f, 18, bold = true, color = PRIMARY, align = TextAligns.CENTER).let {
                it.copy(properties = it.properties + (PropertyKeys.NAME to "chk_mark") + (PropertyKeys.VISIBLE to "false"))
            }
            val box = Interactions.withClickAction(
                column(
                    fixed(28f), 28f, spacing = 0, children = listOf(mark),
                    extra = mapOf(PropertyKeys.BORDER_COLOR to PRIMARY, PropertyKeys.BORDER_WIDTH to "2", PropertyKeys.CORNER_RADIUS to "6")
                ),
                steps
            )
            row(
                fill(), 48f, padding = 10, spacing = 12,
                children = listOf(box, text(t(R.string.pc_cb_label), fixed(100f), 24f, 14, weight = 1))
            )
        },
        preset("pro_switch_row", R.string.pc_ctl_switch, PresetCategory.INPUTS) { t ->
            val toggle = ClickAction.Blocks(listOf(
                com.arabicuieditor.data.script.Step(com.arabicuieditor.data.script.Steps.TOGGLE, "sw_off"),
                com.arabicuieditor.data.script.Step(com.arabicuieditor.data.script.Steps.TOGGLE, "sw_on")
            ))
            fun track(name: String, bg: String, knobX: Float, visible: Boolean): UiElement {
                val knob = node(ElementTypes.CONTAINER, fixed(24f), fixed(24f), mapOf(PropertyKeys.BG_COLOR to "#FFFFFFFF", PropertyKeys.CORNER_RADIUS to "12"))
                    .let { it.copy(layout = it.layout.copy(x = knobX, y = 3f)) }
                val props = mapOf(PropertyKeys.BG_COLOR to bg, PropertyKeys.CORNER_RADIUS to "15", PropertyKeys.NAME to name) +
                    if (visible) emptyMap() else mapOf(PropertyKeys.VISIBLE to "false")
                return Interactions.withClickAction(node(ElementTypes.CONTAINER, fixed(52f), fixed(30f), props, ChildLayout.ABSOLUTE, listOf(knob)), toggle)
            }
            val holder = node(
                ElementTypes.CONTAINER, fixed(52f), fixed(30f), emptyMap(), ChildLayout.ABSOLUTE,
                listOf(track("sw_off", MUTED, 3f, true), track("sw_on", PRIMARY, 25f, false))
            )
            row(
                fill(), 52f, padding = 10, spacing = 12,
                children = listOf(text(t(R.string.pc_sw_label), fixed(100f), 24f, 14, weight = 1), holder)
            )
        },
        preset("pro_dropdown", R.string.pc_ctl_dropdown, PresetCategory.INPUTS) { t ->
            fun option(label: Int) = Interactions.withClickAction(
                button(t(label), fill(), 40f, BtnStyle.TEXT),
                ClickAction.Blocks(listOf(
                    com.arabicuieditor.data.script.Step(com.arabicuieditor.data.script.Steps.TEXT, "dd_btn", t(label)),
                    com.arabicuieditor.data.script.Step(com.arabicuieditor.data.script.Steps.HIDE, "dd_opts")
                ))
            )
            val opts = column(
                fill(), 136f, spacing = 0, type = ElementTypes.CARD,
                children = listOf(option(R.string.pc_dd_o1), option(R.string.pc_dd_o2), option(R.string.pc_dd_o3))
            ).let { it.copy(properties = it.properties + (PropertyKeys.NAME to "dd_opts") + (PropertyKeys.VISIBLE to "false")) }
            val head = Interactions.withClickAction(
                button(t(R.string.pc_dd_placeholder), fill(), 44f, BtnStyle.OUTLINE).let { it.copy(properties = it.properties + (PropertyKeys.NAME to "dd_btn")) },
                ClickAction.Blocks(listOf(com.arabicuieditor.data.script.Step(com.arabicuieditor.data.script.Steps.TOGGLE, "dd_opts")))
            )
            column(fill(), 200f, padding = 10, spacing = 6, children = listOf(head, opts))
                .let { it.copy(layout = it.layout.copy(height = SizeSpec(SizeMode.WRAP_CONTENT, 200f))) }
        },
        // روابط حسابي: 0 المحفوظة، 1 سجل البحث، 2 المميزة، 3 تقييم البائع
        preset("pro_account_links", R.string.pm_account_links, PresetCategory.MENUS) { t ->
            column(
                fill(), 208f, padding = 12, spacing = 8,
                children = listOf(
                    button(t(R.string.pm_al_saved), fill(), 40f, BtnStyle.OUTLINE),
                    button(t(R.string.pm_al_history), fill(), 40f, BtnStyle.OUTLINE),
                    button(t(R.string.pm_al_featured), fill(), 40f, BtnStyle.OUTLINE),
                    button(t(R.string.pm_al_review), fill(), 40f, BtnStyle.OUTLINE)
                )
            )
        },
        preset("pro_search_history", R.string.pm_search_history, PresetCategory.LISTS) { t ->
            node(
                ElementTypes.LIST, fill(), fixed(176f),
                mapOf(PropertyKeys.ITEMS to t(R.string.pm_sh_items), PropertyKeys.ITEM_HEIGHT to "44")
            )
        },
        // باقة مميزة: 3 زر الاختيار
        preset("pro_featured_plan", R.string.pm_featured_plan, PresetCategory.CARDS) { t ->
            column(
                fill(), 210f, padding = 12, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pm_fp_title), fill(), 26f, 17, bold = true),
                    text(t(R.string.pm_fp_price), fill(), 24f, 16, bold = true, color = PRIMARY),
                    text(t(R.string.pm_fp_benefits), fill(), 60f, 13, color = MUTED),
                    steps(button(t(R.string.pm_fp_btn), fill(), 44f), toast(t(R.string.pm_toast_plan)))
                )
            )
        },
        // تقييم البائع: 3 إرسال
        preset("pro_review_form", R.string.pm_review_form, PresetCategory.INPUTS) { t ->
            column(
                fill(), 270f, padding = 14, spacing = 8, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pm_rv_title), fill(), 26f, 17, bold = true),
                    input(t(R.string.pm_rv_stars), fill(), 48f), input(t(R.string.pm_rv_comment), fill(), 100f),
                    button(t(R.string.pm_rv_send), fill(), 44f)
                )
            )
        },
        // تنبيه حدود الواجهة الرسمية
        preset("pro_api_note", R.string.pc_api_note, PresetCategory.CARDS) { t ->
            column(
                fill(), 130f, padding = 12, spacing = 4, type = ElementTypes.CARD,
                children = listOf(
                    text(t(R.string.pc_an_title), fill(), 24f, 14, bold = true),
                    text(t(R.string.pc_an_body), fill(), 80f, 12, color = MUTED)
                )
            )
        }
    )
}
