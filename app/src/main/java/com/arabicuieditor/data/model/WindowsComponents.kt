package com.arabicuieditor.data.model

import com.arabicuieditor.R

/**
 * مكوّنات واجهات Windows Desktop الجاهزة (قائمة جانبية، أشرطة، جدول، نوافذ، تسجيل دخول، إعدادات، لوحة تحكم...).
 * تُبنى من العناصر والحاويات المرنة نفسها وبمراجع نظام التصميم، فتتبع الثيم والوضع الداكن. تصميم واجهة فقط.
 */
internal object WindowsComponents {
    private val P = ProComponents

    private fun nav(label: String, icon: String, selected: Boolean): UiElement {
        val extra = if (selected) mapOf(PropertyKeys.BG_COLOR to P.PRIMARY, PropertyKeys.CORNER_RADIUS to "6") else mapOf(PropertyKeys.CORNER_RADIUS to "6")
        val item = P.row(
            P.fill(), 40f, spacing = 12, padding = 10, extra = extra,
            children = listOf(
                P.image(P.fixed(20f), 20f, icon = icon, iconColor = if (selected) P.ON_PRIMARY else P.MUTED),
                P.text(label, P.fill(), 24f, 14, color = if (selected) P.ON_PRIMARY else null, weight = 1)
            )
        )
        return item
    }

    private fun gap(weight: Int = 1) = P.node(ElementTypes.SPACER, P.fixed(8f), P.fixed(8f), mapOf(PropertyKeys.WEIGHT to "$weight"))

    private fun cell(value: String, bold: Boolean = false, muted: Boolean = false) =
        P.text(value, P.fill(), 22f, 13, bold = bold, color = if (muted) P.MUTED else null, weight = 1)

    private fun tableRow(vararg cells: String, header: Boolean = false) =
        P.row(P.fill(), 36f, spacing = 8, padding = 8, children = cells.map { cell(it, bold = header, muted = header) })

    private fun stat(label: String, value: String) = P.column(
        P.fill(), 96f, spacing = 4, padding = 16, type = ElementTypes.CARD, weight = 1,
        children = listOf(P.text(label, P.fill(), 20f, 13, color = P.MUTED), P.text(value, P.fill(), 36f, 28, bold = true))
    )

    private fun settingRow(label: String, value: String) = P.row(
        P.fill(), 44f, spacing = 8, padding = 8,
        children = listOf(P.text(label, P.fill(), 22f, 14, weight = 1), P.button(value, P.fixed(120f), 32f, ProComponents.BtnStyle.OUTLINE))
    )

    val presets: List<ComponentPreset> = listOf(

        P.preset("win_title_bar", R.string.wn_p_title_bar, PresetCategory.WINDOWS) { t ->
            P.row(
                P.fill(), 32f, spacing = 8, padding = 8, extra = mapOf(PropertyKeys.BG_COLOR to P.SURFACE),
                children = listOf(
                    P.image(P.fixed(16f), 16f, icon = "Build", iconColor = P.PRIMARY),
                    P.text(t(R.string.wn_app_name), P.fill(), 20f, 12, weight = 1),
                    P.button("─", P.fixed(40f), 28f, ProComponents.BtnStyle.TEXT),
                    P.button("▢", P.fixed(40f), 28f, ProComponents.BtnStyle.TEXT),
                    P.button("✕", P.fixed(40f), 28f, ProComponents.BtnStyle.DANGER)
                )
            )
        },

        P.preset("win_sidebar", R.string.wn_p_sidebar, PresetCategory.WINDOWS) { t ->
            P.column(
                P.fixed(240f), 640f, spacing = 4, padding = 12, extra = mapOf(PropertyKeys.BG_COLOR to P.SURFACE),
                children = listOf(
                    P.text(t(R.string.wn_sidebar_title), P.fill(), 36f, 18, bold = true),
                    P.divider(),
                    nav(t(R.string.wn_nav_home), "Home", true),
                    nav(t(R.string.wn_nav_files), "Place", false),
                    nav(t(R.string.wn_nav_reports), "DateRange", false),
                    nav(t(R.string.wn_nav_users), "Person", false),
                    gap(),
                    P.divider(),
                    nav(t(R.string.wn_nav_settings), "Settings", false)
                )
            )
        },

        P.preset("win_toolbar", R.string.wn_p_toolbar, PresetCategory.WINDOWS) { t ->
            P.row(
                P.fill(), 48f, spacing = 8, padding = 8, extra = mapOf(PropertyKeys.BG_COLOR to P.SURFACE),
                children = listOf(
                    P.button(t(R.string.wn_tb_new), P.fixed(80f), 32f),
                    P.button(t(R.string.wn_tb_open), P.fixed(80f), 32f, ProComponents.BtnStyle.OUTLINE),
                    P.button(t(R.string.wn_tb_save), P.fixed(80f), 32f, ProComponents.BtnStyle.OUTLINE),
                    gap(),
                    P.input(t(R.string.wn_search), P.fixed(240f), 32f)
                )
            )
        },

        P.preset("win_tabs", R.string.wn_p_tabs, PresetCategory.WINDOWS) { t ->
            P.column(
                P.fill(), 220f, spacing = 0,
                children = listOf(
                    P.row(
                        P.fill(), 40f, spacing = 4,
                        children = listOf(
                            P.button(t(R.string.wn_tab_general), P.fixed(110f), 36f),
                            P.button(t(R.string.wn_tab_advanced), P.fixed(110f), 36f, ProComponents.BtnStyle.TEXT),
                            P.button(t(R.string.wn_tab_about), P.fixed(110f), 36f, ProComponents.BtnStyle.TEXT)
                        )
                    ),
                    P.column(
                        P.fill(), 180f, padding = 16, type = ElementTypes.CARD, weight = 1,
                        children = listOf(P.text(t(R.string.wn_tab_body), P.fill(), 24f, 14))
                    )
                )
            )
        },

        P.preset("win_table", R.string.wn_p_table, PresetCategory.WINDOWS) { t ->
            P.column(
                P.fill(), 252f, spacing = 0, padding = 8, type = ElementTypes.CARD,
                children = listOf(
                    tableRow(t(R.string.wn_tbl_name), t(R.string.wn_tbl_role), t(R.string.wn_tbl_status), t(R.string.wn_tbl_date), header = true),
                    P.divider(),
                    tableRow(t(R.string.wn_row_n1), t(R.string.wn_row_r1), t(R.string.wn_row_s1), "2026-01-05"),
                    tableRow(t(R.string.wn_row_n2), t(R.string.wn_row_r2), t(R.string.wn_row_s1), "2026-01-12"),
                    tableRow(t(R.string.wn_row_n3), t(R.string.wn_row_r3), t(R.string.wn_row_s2), "2026-02-01"),
                    tableRow(t(R.string.wn_row_n4), t(R.string.wn_row_r2), t(R.string.wn_row_s1), "2026-02-14")
                )
            )
        },

        P.preset("win_dialog", R.string.wn_p_dialog, PresetCategory.WINDOWS) { t ->
            P.column(
                P.fixed(440f), 190f, spacing = 12, padding = 20, type = ElementTypes.CARD,
                children = listOf(
                    P.text(t(R.string.wn_dlg_title), P.fill(), 30f, 20, bold = true),
                    P.text(t(R.string.wn_dlg_body), P.fill(), 44f, 14, color = P.MUTED),
                    P.row(
                        P.fill(), 40f, spacing = 8, extra = mapOf(PropertyKeys.ARRANGEMENT to Arrangements.END),
                        children = listOf(
                            P.button(t(R.string.wn_dlg_cancel), P.fixed(90f), 36f, ProComponents.BtnStyle.TEXT),
                            P.button(t(R.string.wn_dlg_dont), P.fixed(110f), 36f, ProComponents.BtnStyle.OUTLINE),
                            P.button(t(R.string.wn_tb_save), P.fixed(90f), 36f)
                        )
                    )
                )
            )
        },

        P.preset("win_login", R.string.wn_p_login, PresetCategory.WINDOWS) { t ->
            P.column(
                P.fixed(380f), 400f, spacing = 12, padding = 24, type = ElementTypes.CARD,
                extra = mapOf(PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER),
                children = listOf(
                    P.image(P.fixed(64f), 64f, icon = "AccountCircle", iconColor = P.PRIMARY),
                    P.text(t(R.string.wn_login_title), P.fill(), 36f, 24, bold = true, align = TextAligns.CENTER),
                    P.input(t(R.string.wn_login_user)),
                    P.input(t(R.string.wn_login_pass)),
                    P.text(t(R.string.wn_login_remember), P.fill(), 24f, 13, color = P.MUTED),
                    P.button(t(R.string.wn_login_btn), P.fill(), 44f)
                )
            )
        },

        P.preset("win_settings", R.string.wn_p_settings, PresetCategory.WINDOWS) { t ->
            P.column(
                P.fill(), 300f, spacing = 8, padding = 16,
                children = listOf(
                    P.text(t(R.string.wn_set_title), P.fill(), 40f, 26, bold = true),
                    P.column(
                        P.fill(), 168f, spacing = 0, padding = 8, type = ElementTypes.CARD,
                        children = listOf(
                            settingRow(t(R.string.wn_set_theme), t(R.string.wn_set_theme_val)),
                            P.divider(),
                            settingRow(t(R.string.wn_set_lang), t(R.string.wn_set_lang_val)),
                            P.divider(),
                            settingRow(t(R.string.wn_set_notif), t(R.string.wn_set_notif_val))
                        )
                    ),
                    P.button(t(R.string.wn_set_apply), P.fixed(140f), 40f)
                )
            )
        },

        P.preset("win_dashboard", R.string.wn_p_dashboard, PresetCategory.WINDOWS) { t ->
            P.column(
                P.fill(), 360f, spacing = 12, padding = 16,
                children = listOf(
                    P.text(t(R.string.wn_dash_title), P.fill(), 40f, 26, bold = true),
                    P.row(
                        P.fill(), 96f, spacing = 12,
                        children = listOf(
                            stat(t(R.string.wn_stat_users), "1,284"),
                            stat(t(R.string.wn_stat_sales), "12.4k"),
                            stat(t(R.string.wn_stat_tasks), "37")
                        )
                    ),
                    P.column(
                        P.fill(), 200f, spacing = 8, padding = 16, type = ElementTypes.CARD, weight = 1,
                        children = listOf(
                            P.text(t(R.string.wn_stat_chart), P.fill(), 24f, 14, color = P.MUTED),
                            P.image(P.fill(), 140f, radius = 8, weight = 1)
                        )
                    )
                )
            )
        },

        P.preset("win_file_manager", R.string.wn_p_files, PresetCategory.WINDOWS) { t ->
            P.column(
                P.fill(), 340f, spacing = 8, padding = 12,
                children = listOf(
                    P.input(t(R.string.wn_files_path), P.fill(), 36f),
                    P.row(
                        P.fill(), 280f, spacing = 12, weight = 1,
                        children = listOf(
                            P.node(
                                ElementTypes.LIST, P.fixed(200f), P.fixed(280f),
                                mapOf(PropertyKeys.ITEMS to t(R.string.wn_files_folders), PropertyKeys.ITEM_HEIGHT to "36", PropertyKeys.BG_COLOR to P.SURFACE, PropertyKeys.CORNER_RADIUS to "8")
                            ),
                            P.node(
                                ElementTypes.LIST, P.fill(), P.fixed(280f),
                                mapOf(PropertyKeys.ITEMS to t(R.string.wn_files_list), PropertyKeys.ITEM_HEIGHT to "36", PropertyKeys.WEIGHT to "1", PropertyKeys.BG_COLOR to P.SURFACE, PropertyKeys.CORNER_RADIUS to "8")
                            )
                        )
                    )
                )
            )
        },

        P.preset("win_report", R.string.wn_p_report, PresetCategory.WINDOWS) { t ->
            P.column(
                P.fill(), 300f, spacing = 8, padding = 16, type = ElementTypes.CARD,
                children = listOf(
                    P.row(
                        P.fill(), 40f, spacing = 8,
                        children = listOf(
                            P.text(t(R.string.wn_rep_title), P.fill(), 32f, 20, bold = true, weight = 1),
                            P.button(t(R.string.wn_rep_export), P.fixed(100f), 36f, ProComponents.BtnStyle.OUTLINE)
                        )
                    ),
                    P.divider(),
                    tableRow(t(R.string.wn_tbl_name), t(R.string.wn_rep_total), header = true),
                    tableRow(t(R.string.wn_row_n1), "4,200"),
                    tableRow(t(R.string.wn_row_n2), "3,150"),
                    tableRow(t(R.string.wn_row_n3), "2,780"),
                    P.divider(),
                    P.text(t(R.string.wn_rep_total) + ": 10,130", P.fill(), 28f, 16, bold = true)
                )
            )
        },

        P.preset("win_floating_panel", R.string.wn_p_floating, PresetCategory.WINDOWS) { t ->
            P.column(
                P.fixed(300f), 230f, spacing = 8, padding = 12, type = ElementTypes.CARD,
                children = listOf(
                    P.row(
                        P.fill(), 36f, spacing = 8,
                        children = listOf(
                            P.text(t(R.string.wn_float_title), P.fill(), 28f, 16, bold = true, weight = 1),
                            P.button("✕", P.fixed(36f), 32f, ProComponents.BtnStyle.TEXT)
                        )
                    ),
                    P.divider(),
                    settingRow(t(R.string.wn_float_opt1), "✓"),
                    settingRow(t(R.string.wn_float_opt2), "✓"),
                    P.button(t(R.string.wn_set_apply), P.fill(), 36f)
                )
            )
        }
    )
}
