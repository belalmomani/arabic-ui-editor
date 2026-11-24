package com.arabicuieditor.data.model

import androidx.annotation.StringRes
import com.arabicuieditor.R

enum class TemplateCategory(@StringRes val labelRes: Int) {
    STORE(R.string.tcat_store),
    NEWS(R.string.tcat_news),
    CHAT(R.string.tcat_chat),
    EDUCATION(R.string.tcat_education),
    ADMIN(R.string.tcat_admin),
    LOGIN(R.string.tcat_login),
    FOOD(R.string.tcat_food),
    BOOKING(R.string.tcat_booking),
    PROFILE(R.string.tcat_profile),
    REALESTATE(R.string.tcat_realestate)
}

/** شاشة واحدة من قالب: اسمها ومعرّفات المكوّنات الجاهزة التي تتكوّن منها بالترتيب. */
class TemplateScreen(
    @StringRes val nameRes: Int,
    val presetIds: List<String>,
    /** روابط انتقال جاهزة: نقر مكوّن (أو أحد أبنائه) ينقل إلى شاشة أخرى من القالب نفسه. */
    val links: List<TemplateLink> = emptyList()
)

/**
 * رابط داخل شاشة قالب: المكوّن رقم [presetIndex] في قائمة المكوّنات (أو ابنه رقم [childIndex] إن حُدّد)
 * ينقل عند النقر إلى شاشة القالب رقم [target].
 */
class TemplateLink(val presetIndex: Int, val target: Int, val childIndex: Int? = null)

/** قالب تطبيق: شاشة أو أكثر مركّبة من مكوّنات المكتبة الاحترافية بنصوص نموذجية تعدّلها كما تشاء. */
class AppTemplate(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val descRes: Int,
    val category: TemplateCategory,
    val screens: List<TemplateScreen>
)

object Templates {
    private const val PAD = 16
    private const val GAP = 12

    val all: List<AppTemplate> = listOf(
        AppTemplate(
            "tpl_store", R.string.tpl_store, R.string.tpl_store_d, TemplateCategory.STORE,
            listOf(TemplateScreen(R.string.ts_store_home, listOf(
                "pro_appbar", "pro_product_pair", "pro_product_row", "pro_product_row", "pro_bottom_nav"
            )))
        ),
        AppTemplate(
            "tpl_news", R.string.tpl_news, R.string.tpl_news_d, TemplateCategory.NEWS,
            listOf(TemplateScreen(R.string.ts_news_home, listOf(
                "pro_appbar", "pro_news_card", "pro_news_row", "pro_news_row", "pro_bottom_nav"
            )))
        ),
        AppTemplate(
            "tpl_chat", R.string.tpl_chat, R.string.tpl_chat_d, TemplateCategory.CHAT,
            listOf(
                TemplateScreen(R.string.ts_chat_list, listOf(
                    "pro_appbar", "pro_chat_row", "pro_chat_row", "pro_chat_row", "pro_bottom_nav"
                ), links = listOf(TemplateLink(1, 1), TemplateLink(2, 1), TemplateLink(3, 1))),
                TemplateScreen(R.string.ts_chat_room, listOf(
                    "pro_appbar", "pro_chat_in", "pro_chat_out", "pro_chat_in", "pro_chat_input"
                ))
            )
        ),
        AppTemplate(
            "tpl_education", R.string.tpl_education, R.string.tpl_education_d, TemplateCategory.EDUCATION,
            listOf(TemplateScreen(R.string.ts_edu_lessons, listOf(
                "pro_appbar", "pro_tabs", "pro_news_row", "pro_news_row", "pro_news_row"
            )))
        ),
        AppTemplate(
            "tpl_admin", R.string.tpl_admin, R.string.tpl_admin_d, TemplateCategory.ADMIN,
            listOf(TemplateScreen(R.string.ts_admin_settings, listOf(
                "pro_appbar", "pro_profile_header", "pro_menu_settings", "pro_menu_list", "pro_bottom_nav"
            )))
        ),
        AppTemplate(
            "tpl_login", R.string.tpl_login, R.string.tpl_login_d, TemplateCategory.LOGIN,
            listOf(
                TemplateScreen(
                    R.string.ts_login, listOf("pro_login_form"),
                    links = listOf(TemplateLink(0, 2, childIndex = 3), TemplateLink(0, 1, childIndex = 5))
                ),
                TemplateScreen(
                    R.string.ts_signup, listOf("pro_login_signup"),
                    links = listOf(TemplateLink(0, 2, childIndex = 4))
                ),
                TemplateScreen(R.string.ts_otp, listOf("pro_login_otp"))
            )
        ),
        AppTemplate(
            "tpl_food", R.string.tpl_food, R.string.tpl_food_d, TemplateCategory.FOOD,
            listOf(
                TemplateScreen(R.string.ts_food_menu, listOf(
                    "pro_appbar", "pro_tabs", "pro_food_card", "pro_food_card", "pro_product_row", "pro_bottom_nav"
                ), links = listOf(
                    TemplateLink(2, 1), TemplateLink(3, 1), TemplateLink(4, 1),
                    TemplateLink(5, 2, childIndex = 2)
                )),
                TemplateScreen(R.string.ts_food_item, listOf("pro_appbar", "pro_food_card", "pro_product_row")),
                TemplateScreen(R.string.ts_food_cart, listOf(
                    "pro_appbar", "pro_product_row", "pro_product_row", "pro_menu_list"
                ))
            )
        ),
        AppTemplate(
            "tpl_booking", R.string.tpl_booking, R.string.tpl_booking_d, TemplateCategory.BOOKING,
            listOf(
                TemplateScreen(R.string.ts_booking_slots, listOf(
                    "pro_appbar", "pro_tabs", "pro_slot_row", "pro_slot_row", "pro_slot_row", "pro_bottom_nav"
                ), links = listOf(TemplateLink(2, 1), TemplateLink(3, 1), TemplateLink(4, 1))),
                TemplateScreen(
                    R.string.ts_booking_details, listOf("pro_appbar", "pro_news_card", "pro_menu_list"),
                    links = listOf(TemplateLink(1, 2))
                ),
                TemplateScreen(R.string.ts_booking_confirm, listOf("pro_appbar", "pro_dialog_confirm"))
            )
        ),
        AppTemplate(
            "tpl_realestate", R.string.tpl_realestate, R.string.tpl_realestate_d, TemplateCategory.REALESTATE,
            listOf(
                TemplateScreen(R.string.ts_re_list, listOf(
                    "pro_appbar", "pro_tabs", "pro_property_card", "pro_property_card", "pro_bottom_nav"
                ), links = listOf(TemplateLink(2, 1), TemplateLink(3, 1))),
                TemplateScreen(R.string.ts_re_details, listOf("pro_appbar", "pro_property_card", "pro_menu_list"))
            )
        ),
        AppTemplate(
            "tpl_posts", R.string.tpl_posts, R.string.tpl_posts_d, TemplateCategory.NEWS,
            listOf(
                // 0 البحث، 1 النتائج، 2 المكتبة، 3 الإعدادات، 4 تعديل منشور
                TemplateScreen(R.string.ts_posts_search, listOf("pro_appbar", "pro_search_form", "pro_api_note"),
                    listOf(TemplateLink(1, 1, childIndex = 4), TemplateLink(1, 3, childIndex = 7))),
                TemplateScreen(R.string.ts_posts_results, listOf("pro_appbar", "pro_post_tools", "pro_post_card", "pro_post_card"),
                    listOf(TemplateLink(1, 2, childIndex = 3), TemplateLink(2, 4, childIndex = 5), TemplateLink(3, 4, childIndex = 5))),
                TemplateScreen(R.string.ts_posts_library, listOf("pro_appbar", "pro_post_tools", "pro_tabs", "pro_post_card", "pro_post_card", "pro_export_bar"),
                    listOf(TemplateLink(3, 4, childIndex = 5), TemplateLink(4, 4, childIndex = 5))),
                TemplateScreen(R.string.ts_posts_settings, listOf("pro_appbar", "pro_search_settings", "pro_api_note", "pro_switch_row"),
                    listOf(TemplateLink(1, 0, childIndex = 6))),
                TemplateScreen(R.string.ts_posts_edit, listOf("pro_appbar", "pro_post_edit"),
                    listOf(TemplateLink(1, 2, childIndex = 2), TemplateLink(1, 2, childIndex = 3)))
            )
        ),
        AppTemplate(
            "tpl_souq", R.string.tpl_souq, R.string.tpl_souq_d, TemplateCategory.STORE,
            listOf(
                // 0 الرئيسية، 1 البحث، 2 التفاصيل، 3 إضافة، 4 حسابي، 5 الرسائل، 6 الإبلاغ
                TemplateScreen(R.string.ts_souq_home, listOf("pro_appbar", "pro_ad_search", "pro_category_list", "pro_ad_row", "pro_ad_row", "pro_market_nav"),
                    listOf(TemplateLink(1, 1, childIndex = 1), TemplateLink(3, 2), TemplateLink(4, 2),
                        TemplateLink(5, 1, childIndex = 1), TemplateLink(5, 3, childIndex = 2),
                        TemplateLink(5, 5, childIndex = 3), TemplateLink(5, 4, childIndex = 4))),
                TemplateScreen(R.string.ts_souq_search, listOf("pro_appbar", "pro_filter_chips", "pro_ad_filter", "pro_ad_row", "pro_ad_row"),
                    listOf(TemplateLink(3, 2), TemplateLink(4, 2))),
                TemplateScreen(R.string.ts_souq_details, listOf("pro_appbar", "pro_ad_details", "pro_rating_row", "pro_map_card"),
                    listOf(TemplateLink(1, 5, childIndex = 8), TemplateLink(1, 6, childIndex = 11))),
                TemplateScreen(R.string.ts_souq_add, listOf("pro_appbar", "pro_ad_form", "pro_location_row", "pro_dropdown", "pro_checkbox_row"),
                    listOf(TemplateLink(1, 2, childIndex = 9), TemplateLink(1, 0, childIndex = 10))),
                TemplateScreen(R.string.ts_souq_account, listOf("pro_appbar", "pro_profile_header", "pro_account_menu", "pro_switch_row", "pro_account_links"),
                    listOf(TemplateLink(4, 7, childIndex = 0), TemplateLink(4, 8, childIndex = 1), TemplateLink(4, 9, childIndex = 2), TemplateLink(4, 10, childIndex = 3))),
                TemplateScreen(R.string.ts_souq_msgs, listOf("pro_appbar", "pro_chat_row", "pro_chat_row", "pro_chat_row")),
                TemplateScreen(R.string.ts_souq_report, listOf("pro_appbar", "pro_report_form"),
                    listOf(TemplateLink(1, 2, childIndex = 3))),
                // 7 المحفوظة، 8 سجل البحث، 9 المميزة، 10 تقييم البائع
                TemplateScreen(R.string.ts_souq_saved, listOf("pro_appbar", "pro_ad_row", "pro_ad_row"),
                    listOf(TemplateLink(1, 2), TemplateLink(2, 2))),
                TemplateScreen(R.string.ts_souq_history, listOf("pro_appbar", "pro_search_history")),
                TemplateScreen(R.string.ts_souq_featured, listOf("pro_appbar", "pro_featured_plan", "pro_featured_plan")),
                TemplateScreen(R.string.ts_souq_review, listOf("pro_appbar", "pro_rating_row", "pro_review_form"),
                    listOf(TemplateLink(2, 4, childIndex = 3)))
            )
        ),
        AppTemplate(
            "tpl_souq_admin", R.string.tpl_souq_admin, R.string.tpl_souq_admin_d, TemplateCategory.ADMIN,
            listOf(
                TemplateScreen(R.string.ts_sadmin_dash, listOf("pro_appbar", "pro_admin_stats", "pro_admin_menu")),
                TemplateScreen(R.string.ts_sadmin_review, listOf("pro_appbar", "pro_tabs", "pro_admin_ad_row", "pro_admin_ad_row", "pro_admin_ad_row")),
                TemplateScreen(R.string.ts_sadmin_users, listOf("pro_appbar", "pro_tabs", "pro_chat_row", "pro_chat_row", "pro_chat_row"))
            )
        ),
        AppTemplate(
            "tpl_profile", R.string.tpl_profile, R.string.tpl_profile_d, TemplateCategory.PROFILE,
            listOf(
                TemplateScreen(R.string.ts_profile_home, listOf(
                    "pro_appbar", "pro_menu_settings", "pro_menu_list", "pro_bottom_nav"
                )),
                TemplateScreen(R.string.ts_profile_phone, listOf("pro_login_phone"))
            )
        )
    )

    /** يبحث في أسماء القوالب وأوصافها (نصوص معروضة) ؛ الفراغ يعيد الكل. */
    fun filter(
        category: TemplateCategory?, query: String, label: (AppTemplate) -> String
    ): List<AppTemplate> {
        val q = query.trim()
        return all.filter { t ->
            (category == null || t.category == category) && (q.isEmpty() || label(t).contains(q, ignoreCase = true))
        }
    }

    /** يطبّق [links] على جذور المكوّنات (كل مكوّن قد يُنتج أكثر من جذر؛ الرابط على أولها). */
    private fun linked(perPreset: List<List<UiElement>>, links: List<TemplateLink>, screenIds: List<String>): List<List<UiElement>> {
        if (links.isEmpty()) return perPreset
        val out = perPreset.map { it.toMutableList() }
        for (l in links) {
            val target = screenIds.getOrNull(l.target) ?: continue
            val roots = out.getOrNull(l.presetIndex)?.takeIf { it.isNotEmpty() } ?: continue
            val action = ClickAction.Navigate(target)
            val root = roots[0]
            roots[0] = if (l.childIndex == null) Interactions.withClickAction(root, action)
            else root.copy(children = root.children.mapIndexed { i, c ->
                if (i == l.childIndex) Interactions.withClickAction(c, action) else c
            })
        }
        return out
    }

    /**
     * يبني شاشات القالب (بمعرّفات جديدة وأسماء فريدة مقارنةً بـ [existing]).
     * الشاشة عمود مركَّز بحشوة وفواصل؛ معرّف مكوّن مجهول يُتخطّى.
     */
    fun build(template: AppTemplate, existing: List<UiScreen>, text: (Int) -> String): List<UiScreen> {
        val taken = ArrayList(existing)
        val out = ArrayList<UiScreen>()
        val screenIds = template.screens.map { newId() }
        template.screens.forEachIndexed { index, spec ->
            // أسماء الأهداف داخل الشاشة فريدة: نسخ المكوّن المتكررة لا تتحكم ببعضها.
            val soFar = ArrayList<UiElement>()
            val perPreset = spec.presetIds.map { id ->
                val built = ComponentLibrary.presets.firstOrNull { it.id == id }?.build(text).orEmpty()
                ElementOps.relinkBlockTargets(ElementTree.flatten(soFar), built).also { soFar += it }
            }
            val elements = linked(perPreset, spec.links, screenIds).flatten()
            val screen = UiScreen(
                id = screenIds[index],
                name = ScreenOps.uniqueName(taken, text(spec.nameRes)),
                elements = elements,
                childLayout = ChildLayout.COLUMN,
                properties = mapOf(
                    PropertyKeys.CHILD_ALIGN_H to Aligns.CENTER,
                    PropertyKeys.SPACING to GAP.toString(),
                    PropertyKeys.PADDING_START to PAD.toString(),
                    PropertyKeys.PADDING_TOP to PAD.toString(),
                    PropertyKeys.PADDING_END to PAD.toString(),
                    PropertyKeys.PADDING_BOTTOM to PAD.toString()
                )
            )
            taken += screen
            out += screen
        }
        return out
    }
}
