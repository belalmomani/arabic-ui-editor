package com.arabicuieditor.data.planner

import androidx.annotation.StringRes
import com.arabicuieditor.R
import com.arabicuieditor.data.model.AppTemplate
import com.arabicuieditor.data.model.DesignSystem
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.TemplateCategory
import com.arabicuieditor.data.model.TemplateLink
import com.arabicuieditor.data.model.TemplateScreen
import com.arabicuieditor.data.model.Templates
import com.arabicuieditor.data.model.Tokens
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.newId
import com.arabicuieditor.data.model.withProp

/** نوع التطبيق: يحدد الشاشة الرئيسية وشاشة التفاصيل والمزايا الافتراضية. */
enum class PlanKind(@StringRes val labelRes: Int, val defaults: Set<PlanFeature>) {
    RESTAURANT(R.string.pk_restaurant, setOf(PlanFeature.DETAILS, PlanFeature.CART, PlanFeature.CONTACT)),
    SHOP(R.string.pk_shop, setOf(PlanFeature.LOGIN, PlanFeature.DETAILS, PlanFeature.CART, PlanFeature.PROFILE)),
    CLINIC(R.string.pk_clinic, setOf(PlanFeature.DETAILS, PlanFeature.BOOKING, PlanFeature.CONTACT)),
    SERVICES(R.string.pk_services, setOf(PlanFeature.DETAILS, PlanFeature.BOOKING, PlanFeature.CONTACT)),
    NEWS(R.string.pk_news, setOf(PlanFeature.DETAILS, PlanFeature.CONTACT)),
    PORTFOLIO(R.string.pk_portfolio, setOf(PlanFeature.DETAILS, PlanFeature.CONTACT)),
    EDUCATION(R.string.pk_education, setOf(PlanFeature.DETAILS, PlanFeature.PROFILE)),
    REALESTATE(R.string.pk_realestate, setOf(PlanFeature.DETAILS, PlanFeature.BOOKING, PlanFeature.CONTACT)),
    BOOKING(R.string.pk_booking, setOf(PlanFeature.DETAILS, PlanFeature.BOOKING, PlanFeature.CONTACT)),
    /** باحث منشورات: قالب جاهز بخمس شاشات (المزايا لا تؤثر فيه). */
    POSTS(R.string.pk_posts, emptySet()),
    /** سوقنا: تطبيق المستخدم من قالب جاهز بسبع شاشات. */
    CLASSIFIEDS(R.string.pk_souq, emptySet())
}

/** ميزة اختيارية: تضيف شاشة أو أكثر وتربطها بالبقية. */
enum class PlanFeature(@StringRes val labelRes: Int) {
    LOGIN(R.string.pf_login),
    DETAILS(R.string.pf_details),
    CART(R.string.pf_cart),
    BOOKING(R.string.pf_booking),
    CONTACT(R.string.pf_contact),
    PROFILE(R.string.pf_profile)
}

/** ألوان أساسية جاهزة (ARGB). */
object PlanColors {
    val all: List<Int> = listOf(
        0xFF23866B.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFFE53935.toInt(),
        0xFFFB8C00.toInt(), 0xFF00897B.toInt(), 0xFF3949AB.toInt(), 0xFF212121.toInt()
    )
}

data class AppPlan(
    val kind: PlanKind,
    val name: String,
    val features: Set<PlanFeature> = kind.defaults,
    val primary: Int = PlanColors.all[0]
)

/**
 * «مخطِّط التطبيق»: يحوّل نوع التطبيق والمزايا المختارة إلى شاشات مترابطة من مكوّنات المكتبة
 * بلا ذكاء اصطناعي ولا إنترنت. يعيد استعمال Templates.build فتبقى الشاشات والروابط بالقواعد نفسها.
 */
object AppPlanner {
    private class Spec(@StringRes val nameRes: Int, val presets: List<String>, val links: MutableList<TemplateLink> = mutableListOf())

    private class Home(@StringRes val nameRes: Int, val presets: List<String>, val itemLinks: List<Int>)

    private fun home(kind: PlanKind) = when (kind) {
        PlanKind.RESTAURANT -> Home(R.string.ps_home_restaurant, listOf("pro_appbar", "pro_tabs", "pro_food_card", "pro_food_card", "pro_product_row", "pro_bottom_nav"), listOf(2, 3, 4))
        PlanKind.SHOP -> Home(R.string.ps_home_shop, listOf("pro_appbar", "pro_product_pair", "pro_product_row", "pro_product_row", "pro_bottom_nav"), listOf(1, 2, 3))
        PlanKind.CLINIC -> Home(R.string.ps_home_clinic, listOf("pro_appbar", "pro_profile_header", "pro_menu_list", "pro_slot_row", "pro_bottom_nav"), listOf(1))
        PlanKind.SERVICES -> Home(R.string.ps_home_services, listOf("pro_appbar", "pro_news_card", "pro_menu_list", "pro_news_row", "pro_bottom_nav"), listOf(1, 3))
        PlanKind.NEWS -> Home(R.string.ps_home_news, listOf("pro_appbar", "pro_tabs", "pro_news_card", "pro_news_row", "pro_news_more", "pro_bottom_nav"), listOf(2, 3))
        PlanKind.PORTFOLIO -> Home(R.string.ps_home_portfolio, listOf("pro_appbar", "pro_profile_header", "pro_menu_list", "pro_product_pair", "pro_bottom_nav"), listOf(3))
        PlanKind.EDUCATION -> Home(R.string.ps_home_education, listOf("pro_appbar", "pro_tabs", "pro_news_row", "pro_news_row", "pro_news_row", "pro_bottom_nav"), listOf(2, 3, 4))
        PlanKind.REALESTATE -> Home(R.string.ps_home_realestate, listOf("pro_appbar", "pro_tabs", "pro_property_card", "pro_property_card", "pro_bottom_nav"), listOf(2, 3))
        PlanKind.CLASSIFIEDS -> Home(R.string.ts_souq_home, listOf("pro_appbar"), emptyList())
        PlanKind.POSTS -> Home(R.string.ts_posts_search, listOf("pro_appbar", "pro_search_form", "pro_api_note"), emptyList())
        PlanKind.BOOKING -> Home(R.string.ps_home_booking, listOf("pro_appbar", "pro_news_card", "pro_menu_list", "pro_slot_row", "pro_bottom_nav"), listOf(1))
    }

    private fun details(kind: PlanKind): Pair<Int, List<String>> = when (kind) {
        PlanKind.RESTAURANT -> R.string.ps_details_restaurant to listOf("pro_appbar", "pro_food_card", "pro_product_row")
        PlanKind.SHOP -> R.string.ps_details_shop to listOf("pro_appbar", "pro_product_card", "pro_product_row")
        PlanKind.CLINIC -> R.string.ps_details_clinic to listOf("pro_appbar", "pro_profile_header", "pro_menu_list")
        PlanKind.SERVICES -> R.string.ps_details_services to listOf("pro_appbar", "pro_news_card", "pro_menu_list")
        PlanKind.NEWS -> R.string.ps_details_news to listOf("pro_appbar", "pro_news_card", "pro_menu_list")
        PlanKind.PORTFOLIO -> R.string.ps_details_portfolio to listOf("pro_appbar", "pro_product_card", "pro_menu_list")
        PlanKind.EDUCATION -> R.string.ps_details_education to listOf("pro_appbar", "pro_news_card", "pro_menu_list")
        PlanKind.REALESTATE -> R.string.ps_details_realestate to listOf("pro_appbar", "pro_property_card", "pro_menu_list")
        PlanKind.CLASSIFIEDS -> R.string.ts_souq_details to listOf("pro_appbar", "pro_ad_details")
        PlanKind.POSTS -> R.string.ts_posts_results to listOf("pro_appbar", "pro_post_card")
        PlanKind.BOOKING -> R.string.ps_details_booking to listOf("pro_appbar", "pro_news_card", "pro_menu_list")
    }

    /** الشاشات بالترتيب مع الروابط (الهدف = فهرس داخل القائمة النهائية). */
    private fun specs(plan: AppPlan): List<Spec> {
        val readyId = when (plan.kind) { PlanKind.POSTS -> "tpl_posts"; PlanKind.CLASSIFIEDS -> "tpl_souq"; else -> null }
        if (readyId != null) {
            val t = Templates.all.first { it.id == readyId }
            return t.screens.map { Spec(it.nameRes, it.presetIds, it.links.toMutableList()) }
        }
        val f = plan.features
        val kind = plan.kind
        val h = home(kind)
        // ترتيب المفاتيح: تسجيل الدخول؟ ← الرئيسية ← تفاصيل؟ ← سلة؟ ← حجز؟ ← تأكيد؟ ← حسابي؟ ← تواصل؟
        val order = ArrayList<String>()
        if (PlanFeature.LOGIN in f) order += "login"
        order += "home"
        if (PlanFeature.DETAILS in f) order += "details"
        if (PlanFeature.CART in f) order += "cart"
        if (PlanFeature.BOOKING in f) { order += "booking"; order += "confirm" }
        if (PlanFeature.PROFILE in f) order += "profile"
        if (PlanFeature.CONTACT in f) order += "contact"
        fun idx(key: String) = order.indexOf(key).takeIf { it >= 0 }

        val out = LinkedHashMap<String, Spec>()
        if ("login" in order) out["login"] = Spec(R.string.ps_login, listOf("pro_login_form")).also {
            idx("home")?.let { t -> it.links += TemplateLink(0, t, childIndex = 3) }
        }
        val homeSpec = Spec(h.nameRes, h.presets)
        // عناصر الرئيسية تفتح التفاصيل.
        idx("details")?.let { t -> h.itemLinks.forEach { i -> homeSpec.links += TemplateLink(i, t) } }
        // العيادة: صف الموعد في الرئيسية يفتح الحجز.
        if (kind == PlanKind.CLINIC || kind == PlanKind.BOOKING) idx("booking")?.let { t -> homeSpec.links += TemplateLink(3, t) }
        // الشريط السفلي: العنصر 1 (بحث) ← حجز، 2 (سلة) ← سلة، 3 (حسابي) ← حسابي أو تواصل.
        val navIndex = h.presets.lastIndex
        if (kind != PlanKind.CLINIC && kind != PlanKind.BOOKING) idx("booking")?.let { homeSpec.links += TemplateLink(navIndex, it, childIndex = 1) }
        idx("cart")?.let { homeSpec.links += TemplateLink(navIndex, it, childIndex = 2) }
        (idx("profile") ?: idx("contact"))?.let { homeSpec.links += TemplateLink(navIndex, it, childIndex = 3) }
        out["home"] = homeSpec
        if ("details" in order) {
            val (n, p) = details(kind)
            out["details"] = Spec(n, p).also { s ->
                (idx("cart") ?: idx("booking"))?.let { t -> s.links += TemplateLink(2, t) }
            }
        }
        if ("cart" in order) out["cart"] = Spec(R.string.ps_cart, listOf("pro_appbar", "pro_product_row", "pro_product_row", "pro_menu_list"))
        if ("booking" in order) out["booking"] = Spec(R.string.ps_booking, listOf("pro_appbar", "pro_tabs", "pro_slot_row", "pro_slot_row", "pro_slot_row")).also { s ->
            idx("confirm")?.let { t -> (2..4).forEach { i -> s.links += TemplateLink(i, t) } }
        }
        if ("confirm" in order) out["confirm"] = Spec(R.string.ps_confirm, listOf("pro_appbar", "pro_dialog_confirm"))
        if ("profile" in order) out["profile"] = Spec(R.string.ps_profile, listOf("pro_appbar", "pro_menu_settings", "pro_menu_list"))
        if ("contact" in order) out["contact"] = Spec(R.string.ps_contact, listOf("pro_appbar", "pro_profile_header", "pro_menu_list"))
        return order.mapNotNull { out[it] }
    }

    /** أسماء الشاشات بالترتيب (لعرض «خريطة التطبيق»). */
    fun screenNames(plan: AppPlan, text: (Int) -> String): List<String> = specs(plan).map { text(it.nameRes) }

    fun template(plan: AppPlan): AppTemplate = AppTemplate(
        "plan_${plan.kind.name.lowercase()}", R.string.tpl_store, R.string.tpl_store_d, TemplateCategory.STORE,
        specs(plan).map { TemplateScreen(it.nameRes, it.presets, it.links) }
    )

    /** يبني المشروع كاملًا: شاشات مترابطة، اللون الأساسي، اسم التطبيق في الشريط العلوي، شاشة البداية الأولى. */
    fun build(plan: AppPlan, text: (Int) -> String, now: Long = System.currentTimeMillis()): Project {
        val name = plan.name.trim().take(60).ifEmpty { text(plan.kind.labelRes) }
        val built = Templates.build(template(plan), emptyList(), text)
        val homeName = text(home(plan.kind).nameRes)
        val screens = ContentSlots.tagPlanned(built.map { s ->
            val title = if (s.name == homeName) name else s.name
            s.copy(elements = s.elements.mapIndexed { i, e -> if (i == 0) withBarTitle(e, title) else e })
        }, text)
        return Project(
            id = newId(), name = name, createdAt = now, updatedAt = now, screens = screens,
            startScreenId = screens.first().id,
            designSystem = DesignSystem.DEFAULT.withColor(Tokens.PRIMARY, plan.primary)
        )
    }

    /** يضع [title] في أول نص داخل الشريط العلوي (أول عنصر في الشاشة إن كان شريطًا). */
    private fun withBarTitle(bar: UiElement, title: String): UiElement {
        val firstText = ElementTree.flatten(listOf(bar)).firstOrNull { it.type == ElementTypes.TEXT } ?: return bar
        return ElementTree.update(listOf(bar), firstText.id) { it.withProp(PropertyKeys.TEXT, title) }.first()
    }
}
