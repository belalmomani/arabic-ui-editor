package com.arabicuieditor.data

import com.arabicuieditor.data.ai.AiProject
import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.storage.ProjectJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProjectTest {
    private val reply = """
        هذا هو التصميم:
        ```json
        {"name":"مطعم الواحة","screens":[
          {"name":"الرئيسية","layout":"column","elements":[
            {"type":"text","text":"مرحبًا","size":22,"bold":true,"color":"#23866B"},
            {"type":"card","layout":"column","padding":12,"children":[{"type":"image","height":140},{"type":"text","text":"وجبة اليوم"}]},
            {"type":"button","text":"القائمة","width":"fill","go":"القائمة"}]},
          {"name":"القائمة","elements":[{"type":"button","text":"رجوع","back":true}]}]}
        ```
        أتمنى أن يعجبك!
    """.trimIndent()

    @Test fun promptContainsIdeaAndSchema() {
        val p = AiProject.prompt("تطبيق لعيادة أسنان")
        assertTrue(p.contains("تطبيق لعيادة أسنان"))
        assertTrue(p.contains("\"screens\""))
        assertTrue(p.contains("go:"))
        assertTrue(AiProject.prompt("   ").contains("اكتب فكرتك"))
    }

    @Test fun extractsJsonFromChattyReply() {
        assertNotNull(AiProject.extractJson(reply))
        assertNull(AiProject.extractJson("لا يوجد json هنا"))
        assertNull(AiProject.extractJson("{ not json }"))
    }

    @Test fun parsesScreensElementsAndNavigation() {
        val p = AiProject.parse(reply)!!
        assertEquals("مطعم الواحة", p.name)
        assertEquals(listOf("الرئيسية", "القائمة"), p.screens.map { it.name })
        val first = p.screens[0]
        val title = first.elements[0]
        assertEquals("مرحبًا", title.properties[PropertyKeys.TEXT])
        assertEquals("22", title.properties[PropertyKeys.FONT_SIZE])
        assertEquals("bold", title.properties[PropertyKeys.FONT_WEIGHT])
        val card = first.elements[1]
        assertEquals(2, card.children.size)
        assertEquals(SizeMode.WRAP_CONTENT, card.layout.height.mode)
        val btn = first.elements[2]
        assertEquals(SizeMode.MATCH_PARENT, btn.layout.width.mode)
        assertEquals(ClickAction.Navigate(p.screens[1].id), Interactions.clickAction(btn))
        assertEquals(ClickAction.Back, Interactions.clickAction(p.screens[1].elements[0]))
    }

    @Test fun toleratesBadInput() {
        val p = AiProject.parse(
            """{"screens":[{"name":"أ","elements":[
              {"type":"hologram","text":"x"},
              {"type":"text","text":"ok","size":9999,"color":"not-a-color"},
              {"type":"button","text":"b","go":"شاشة غير موجودة"}]},
              {"name":"أ","elements":[]}]}"""
        )!!
        assertEquals("مشروع بالذكاء الاصطناعي", p.name)
        assertEquals(listOf("أ", "أ 2"), p.screens.map { it.name })
        val els = p.screens[0].elements
        assertEquals(2, els.size)
        assertFalse(els[0].properties.containsKey(PropertyKeys.TEXT_COLOR))
        assertTrue((els[0].properties[PropertyKeys.FONT_SIZE]?.toInt() ?: 0) <= 200)
        assertEquals(ClickAction.None, Interactions.clickAction(els[1]))
    }

    @Test fun rejectsReplyWithoutScreens() {
        assertNull(AiProject.parse("""{"name":"x","screens":[]}"""))
        assertNull(AiProject.parse("""{"name":"x"}"""))
        assertNull(AiProject.parse("نص عادي"))
    }

    @Test fun limitsScreensAndElements() {
        val screens = (1..30).joinToString(",") { """{"name":"ش$it","elements":[{"type":"text","text":"t"}]}""" }
        assertEquals(AiProject.MAX_SCREENS, AiProject.parse("""{"screens":[$screens]}""")!!.screens.size)
        val many = (1..1000).joinToString(",") { """{"type":"text","text":"t"}""" }
        val p = AiProject.parse("""{"screens":[{"name":"a","elements":[$many]}]}""")!!
        assertEquals(AiProject.MAX_ELEMENTS, ElementTree.flatten(p.screens[0].elements).size)
    }

    @Test fun resultSurvivesStorageRoundTrip() {
        val p = AiProject.parse(reply)!!
        val back = ProjectJsonCodec.decode(ProjectJsonCodec.encode(p))
        assertEquals(p.screens.map { it.name }, back.screens.map { it.name })
        assertTrue(ElementTree.flatten(back.screens[0].elements).any { it.type == ElementTypes.BUTTON })
    }

    private fun planned() = com.arabicuieditor.data.planner.AppPlanner.build(
        com.arabicuieditor.data.planner.AppPlan(com.arabicuieditor.data.planner.PlanKind.RESTAURANT, "مطعم"), { "t$it" }
    )

    @Test fun contentPromptNumbersTextsPerScreen() {
        val p = planned()
        val slots = com.arabicuieditor.data.ai.AiContent.slots(p)
        assertTrue(slots.size > 5)
        val prompt = com.arabicuieditor.data.ai.AiContent.prompt(p, "مطعم شامي")
        assertTrue(prompt.contains("مطعم شامي"))
        assertTrue(prompt.contains("1: "))
        assertTrue(prompt.contains("[شاشة: ${p.screens[0].name}]"))
        assertTrue(prompt.contains("\"texts\""))
    }

    @Test fun contentApplyReplacesOnlyListedTexts() {
        val p = planned()
        val slots = com.arabicuieditor.data.ai.AiContent.slots(p)
        val (np, n) = com.arabicuieditor.data.ai.AiContent.apply(
            p, """```json
            {"texts":{"1":"شاورما دجاج","3":"  35 ريالًا  ","999":"x"}}
            ```"""
        )!!
        assertEquals(2, n)
        val byId = ElementTree.flatten(np.screens.flatMap { it.elements }).associateBy { it.id }
        assertEquals("شاورما دجاج", byId[slots[0].elementId]!!.properties[PropertyKeys.TEXT])
        assertEquals("35 ريالًا", byId[slots[2].elementId]!!.properties[PropertyKeys.TEXT])
        assertEquals(slots[1].text, byId[slots[1].elementId]!!.properties[PropertyKeys.TEXT])
    }

    @Test fun contentApplyRejectsUselessReplies() {
        val p = planned()
        assertNull(com.arabicuieditor.data.ai.AiContent.apply(p, "لا شيء"))
        assertNull(com.arabicuieditor.data.ai.AiContent.apply(p, """{"texts":{}}"""))
        assertNull(com.arabicuieditor.data.ai.AiContent.apply(p, """{"other":1}"""))
    }

    @Test fun parsesReplyWithMarkdownEscapesFromChatCopy() {
        // نسخ الشات يضيف \\[ و \\] قبل الأقواس و \\\\n مزدوجة داخل النصوص.
        val reply = "{ \"name\": \"مطعم\", \"screens\": \\[ { \"name\": \"الرئيسية\", \"layout\": \"column\", \"elements\": \\[ { \"type\": \"list\", \"text\": \"أ\\\\nب\" }, { \"type\": \"button\", \"text\": \"رجوع\", \"back\": true } \\] } \\] }"
        val p = AiProject.parse(reply)
        org.junit.Assert.assertNotNull(p)
        org.junit.Assert.assertEquals(1, p!!.screens.size)
        org.junit.Assert.assertEquals(2, p.screens[0].elements.size)
    }
}
