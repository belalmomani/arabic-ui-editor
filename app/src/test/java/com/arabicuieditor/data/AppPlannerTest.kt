package com.arabicuieditor.data

import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.Tokens
import com.arabicuieditor.data.planner.AppPlan
import com.arabicuieditor.data.planner.AppPlanner
import com.arabicuieditor.data.planner.PlanColors
import com.arabicuieditor.data.planner.PlanFeature
import com.arabicuieditor.data.planner.PlanKind
import com.arabicuieditor.data.storage.ProjectJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPlannerTest {
    private val text: (Int) -> String = { "t$it" }

    @Test fun defaultsPerKind() {
        assertEquals(4, AppPlanner.screenNames(AppPlan(PlanKind.RESTAURANT, "x"), text).size)
        assertEquals(5, AppPlanner.screenNames(AppPlan(PlanKind.SHOP, "x"), text).size)
        // العيادة: رئيسية + طبيب + حجز + تأكيد + تواصل
        assertEquals(5, AppPlanner.screenNames(AppPlan(PlanKind.CLINIC, "x"), text).size)
        // الأخبار والمعرض: رئيسية + تفاصيل + تواصل
        assertEquals(3, AppPlanner.screenNames(AppPlan(PlanKind.NEWS, "x"), text).size)
        assertEquals(3, AppPlanner.screenNames(AppPlan(PlanKind.PORTFOLIO, "x"), text).size)
        // تعليم: رئيسية + تفاصيل + حسابي. عقارات وحجوزات: رئيسية + تفاصيل + حجز + تأكيد + تواصل (5).
        assertEquals(3, AppPlanner.screenNames(AppPlan(PlanKind.EDUCATION, "x"), text).size)
        assertEquals(5, AppPlanner.screenNames(AppPlan(PlanKind.REALESTATE, "x"), text).size)
        assertEquals(5, AppPlanner.screenNames(AppPlan(PlanKind.BOOKING, "x"), text).size)
    }

    @Test fun featuresAddAndRemoveScreens() {
        val base = AppPlan(PlanKind.SHOP, "x", emptySet())
        assertEquals(1, AppPlanner.screenNames(base, text).size)
        assertEquals(2, AppPlanner.screenNames(base.copy(features = setOf(PlanFeature.LOGIN)), text).size)
        assertEquals(3, AppPlanner.screenNames(base.copy(features = setOf(PlanFeature.BOOKING)), text).size)
    }

    @Test fun loginComesFirstAndIsStart() {
        val p = AppPlanner.build(AppPlan(PlanKind.SHOP, "متجري"), text)
        assertEquals(p.screens.first().id, p.startScreenId)
        assertEquals(AppPlanner.screenNames(AppPlan(PlanKind.SHOP, "متجري"), text), p.screens.map { it.name })
    }

    @Test fun projectGetsNameColorAndBarTitle() {
        val plan = AppPlan(PlanKind.RESTAURANT, "  مطعم الواحة  ", primary = PlanColors.all[3])
        val p = AppPlanner.build(plan, text)
        assertEquals("مطعم الواحة", p.name)
        assertEquals(PlanColors.all[3], p.designSystem.color(Tokens.PRIMARY))
        val homeBar = p.screens.first { it.name == text(com.arabicuieditor.R.string.ps_home_restaurant) }.elements[0]
        val title = ElementTree.flatten(listOf(homeBar)).first { it.type == ElementTypes.TEXT }
        assertEquals("مطعم الواحة", title.properties[PropertyKeys.TEXT])
    }

    @Test fun blankNameFallsBackToKindLabel() {
        val p = AppPlanner.build(AppPlan(PlanKind.CLINIC, "   "), text)
        assertEquals(text(PlanKind.CLINIC.labelRes), p.name)
    }

    @Test fun homeItemsOpenDetailsAndAllLinksResolve() {
        for (kind in PlanKind.values()) {
            val p = AppPlanner.build(AppPlan(kind, "x", PlanFeature.values().toSet()), text)
            val ids = p.screens.map { it.id }.toSet()
            val targets = p.screens.flatMap { s -> ElementTree.flatten(s.elements) }
                .mapNotNull { Interactions.clickAction(it) as? ClickAction.Navigate }.map { it.screenId }
            assertTrue("$kind has links", targets.isNotEmpty())
            assertTrue("$kind targets valid", targets.all { it in ids })
        }
        val p = AppPlanner.build(AppPlan(PlanKind.RESTAURANT, "x"), text)
        val details = p.screens.first { it.name == text(com.arabicuieditor.R.string.ps_details_restaurant) }
        val homeTargets = ElementTree.flatten(p.screens[0].elements).mapNotNull { (Interactions.clickAction(it) as? ClickAction.Navigate)?.screenId }
        assertTrue(details.id in homeTargets)
    }

    @Test fun screenNamesAreUnique() {
        for (kind in PlanKind.values()) {
            val names = AppPlanner.screenNames(AppPlan(kind, "x", PlanFeature.values().toSet()), text)
            assertEquals(names.size, names.toSet().size)
        }
    }

    @Test fun builtProjectSurvivesStorageRoundTrip() {
        val p = AppPlanner.build(AppPlan(PlanKind.SERVICES, "خدمات"), text)
        val back = ProjectJsonCodec.decode(ProjectJsonCodec.encode(p))
        assertEquals(p.screens.map { it.name }, back.screens.map { it.name })
        assertEquals(p.designSystem.color(Tokens.PRIMARY), back.designSystem.color(Tokens.PRIMARY))
    }

    @Test fun blueprintListsScreensNavigationAndChecklist() {
        val p = AppPlanner.build(AppPlan(PlanKind.SHOP, "متجر الأمل"), text)
        val md = com.arabicuieditor.data.planner.Blueprint.markdown(p)
        assertTrue(md.startsWith("# مخطط تطبيق «متجر الأمل»"))
        assertEquals(p.screens.size, Regex("(?m)^### ").findAll(md).count())
        p.screens.forEach { assertTrue(md.contains("### ${it.name}")) }
        assertTrue(md.contains(" ← "))
        assertTrue(md.contains("- [ ] "))
        assertTrue(md.contains("وصف مقترح للمتجر"))
        val short = md.lines().first { it.startsWith("- القصير:") }.removePrefix("- القصير: ")
        assertTrue(short.length <= 80)
    }

    @Test fun postsTemplateHasFiveLinkedScreens() {
        val p = AppPlanner.build(AppPlan(PlanKind.POSTS, "باحث"), text)
        assertEquals(5, p.screens.size)
        assertEquals(p.screens[0].id, p.startScreenId)
        val ids = p.screens.map { it.id }.toSet()
        // كل انتقال يشير إلى شاشة موجودة
        val targets = p.screens.flatMap { s -> ElementTree.flatten(s.elements).mapNotNull { (Interactions.clickAction(it) as? ClickAction.Navigate)?.screenId } }
        assertTrue(targets.size >= 8)
        assertTrue(ids.containsAll(targets))
        // زر «بدء البحث» ينقل إلى النتائج
        val start = ElementTree.flatten(p.screens[0].elements).first { it.type == ElementTypes.BUTTON && Interactions.clickAction(it) is ClickAction.Navigate }
        assertEquals(p.screens[1].id, (Interactions.clickAction(start) as ClickAction.Navigate).screenId)
    }

    @Test fun souqHasElevenLinkedScreensAndNavigatesFromBottomBar() {
        val p = AppPlanner.build(AppPlan(PlanKind.CLASSIFIEDS, "سوقنا"), text)
        assertEquals(11, p.screens.size)
        val ids = p.screens.map { it.id }.toSet()
        val targets = p.screens.flatMap { s -> ElementTree.flatten(s.elements).mapNotNull { (Interactions.clickAction(it) as? ClickAction.Navigate)?.screenId } }
        assertTrue(targets.size >= 20)
        assertTrue(ids.containsAll(targets))
        // شريط التنقل في الرئيسية يفتح أربع شاشات مختلفة
        val navTargets = ElementTree.flatten(p.screens[0].elements)
            .mapNotNull { (Interactions.clickAction(it) as? ClickAction.Navigate)?.screenId }.toSet()
        assertTrue(navTargets.containsAll(setOf(p.screens[1].id, p.screens[2].id, p.screens[3].id, p.screens[4].id, p.screens[5].id)))
    }

    @Test fun souqAdminTemplateBuildsAsSeparateScreens() {
        val t = com.arabicuieditor.data.model.Templates.all.first { it.id == "tpl_souq_admin" }
        val screens = com.arabicuieditor.data.model.Templates.build(t, emptyList(), text)
        assertEquals(3, screens.size)
        assertTrue(screens.all { it.elements.isNotEmpty() })
    }
}
