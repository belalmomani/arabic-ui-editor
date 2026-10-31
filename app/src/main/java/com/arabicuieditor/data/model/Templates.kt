package com.arabicuieditor.data.model

import androidx.annotation.StringRes
import com.arabicuieditor.R

enum class TemplateCategory(@StringRes val labelRes: Int) {
    STORE(R.string.tcat_store),
    NEWS(R.string.tcat_news),
    CHAT(R.string.tcat_chat),
    EDUCATION(R.string.tcat_education),
    ADMIN(R.string.tcat_admin),
    LOGIN(R.string.tcat_login)
}

/** شاشة واحدة من قالب: اسمها ومعرّفات المكوّنات الجاهزة التي تتكوّن منها بالترتيب. */
class TemplateScreen(@StringRes val nameRes: Int, val presetIds: List<String>)

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
                )),
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
                "pro_appbar", "pro_menu_settings", "pro_menu_list", "pro_bottom_nav"
            )))
        ),
        AppTemplate(
            "tpl_login", R.string.tpl_login, R.string.tpl_login_d, TemplateCategory.LOGIN,
            listOf(
                TemplateScreen(R.string.ts_login, listOf("pro_login_form")),
                TemplateScreen(R.string.ts_signup, listOf("pro_login_signup")),
                TemplateScreen(R.string.ts_otp, listOf("pro_login_otp"))
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

    /**
     * يبني شاشات القالب (بمعرّفات جديدة وأسماء فريدة مقارنةً بـ [existing]).
     * الشاشة عمود مركَّز بحشوة وفواصل؛ معرّف مكوّن مجهول يُتخطّى.
     */
    fun build(template: AppTemplate, existing: List<UiScreen>, text: (Int) -> String): List<UiScreen> {
        val taken = ArrayList(existing)
        val out = ArrayList<UiScreen>()
        for (spec in template.screens) {
            val elements = spec.presetIds.flatMap { id ->
                ComponentLibrary.presets.firstOrNull { it.id == id }?.build(text).orEmpty()
            }
            val screen = UiScreen(
                id = newId(),
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
