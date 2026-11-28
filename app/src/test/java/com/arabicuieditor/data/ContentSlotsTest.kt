package com.arabicuieditor.data

import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.planner.AppPlan
import com.arabicuieditor.data.planner.AppPlanner
import com.arabicuieditor.data.planner.ContentSlots
import com.arabicuieditor.data.planner.PlanKind
import com.arabicuieditor.data.planner.SlotKind
import com.arabicuieditor.data.storage.ProjectJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentSlotsTest {
    private val text: (Int) -> String = { "t$it" }
    private fun project(kind: PlanKind = PlanKind.CLINIC) = AppPlanner.build(AppPlan(kind, "تطبيقي"), text)
    private fun ids(p: com.arabicuieditor.data.model.Project) =
        p.screens.flatMap { s -> ElementTree.flatten(s.elements).map { it.id } }

    @Test fun contactEmailIsTaggedAndExtracted() {
        val slots = ContentSlots.extract(project())
        assertTrue(slots.any { it.kind == SlotKind.EMAIL })
        assertTrue(slots.any { it.kind == SlotKind.TEXT })
    }

    @Test fun everyKindHasTextSlots() {
        for (k in PlanKind.values()) assertTrue(k.name, ContentSlots.extract(project(k)).any { it.kind == SlotKind.TEXT })
    }

    @Test fun emptyOrInvalidValuesChangeNothing() {
        val p = project()
        val email = ContentSlots.extract(p).first { it.kind == SlotKind.EMAIL }
        assertEquals(p, ContentSlots.apply(p, emptyMap()))
        assertEquals(p, ContentSlots.apply(p, mapOf(email.elementId to "   ")))
        assertEquals(p, ContentSlots.apply(p, mapOf(email.elementId to "not-an-email")))
        assertEquals(p, ContentSlots.apply(p, mapOf("missing" to "x")))
    }

    @Test fun validEmailAndTextApplyWithoutTouchingStructure() {
        val p = project()
        val slots = ContentSlots.extract(p)
        val email = slots.first { it.kind == SlotKind.EMAIL }
        val txt = slots.first { it.kind == SlotKind.TEXT }
        val q = ContentSlots.apply(p, mapOf(email.elementId to "a@b.com", txt.elementId to "عنوان جديد"))
        assertEquals(ids(p), ids(q))
        assertEquals(p.id, q.id)
        assertEquals(p.screens.size, q.screens.size)
        val byId = q.screens.flatMap { ElementTree.flatten(it.elements) }.associateBy { it.id }
        assertEquals("a@b.com", byId[email.elementId]!!.properties[PropertyKeys.TEXT])
        assertEquals("عنوان جديد", byId[txt.elementId]!!.properties[PropertyKeys.TEXT])
        assertEquals(p.screens.map { it.width }, q.screens.map { it.width })
    }

    @Test fun imageApplyReplacesPlaceholderIcon() {
        val p = project(PlanKind.SHOP)
        val img = ContentSlots.extract(p).first { it.kind == SlotKind.IMAGE }
        val q = ContentSlots.apply(p, mapOf(img.elementId to "img_1.png", "x" to "bad"))
        val e = q.screens.flatMap { ElementTree.flatten(it.elements) }.first { it.id == img.elementId }
        assertEquals("img_1.png", e.properties[PropertyKeys.IMAGE_SRC])
        assertFalse(e.properties.containsKey(PropertyKeys.ICON))
        assertEquals(ElementTypes.IMAGE, e.type)
        // اسم ملف غير صالح يُتجاهل
        assertEquals(p, ContentSlots.apply(p, mapOf(img.elementId to "../evil.png")))
    }

    @Test fun slotTagSurvivesJsonRoundTrip() {
        val p = project()
        val back = ProjectJsonCodec.decode(ProjectJsonCodec.encode(p))
        assertEquals(ContentSlots.extract(p).map { it.kind }, ContentSlots.extract(back).map { it.kind })
    }

    @Test fun validators() {
        assertTrue(ContentSlots.isValid(SlotKind.PHONE, "+962 79 123 4567"))
        assertFalse(ContentSlots.isValid(SlotKind.PHONE, "abc"))
        assertTrue(ContentSlots.isValid(SlotKind.EMAIL, "x@y.org"))
        assertFalse(ContentSlots.isValid(SlotKind.EMAIL, "x@y"))
    }

    @Test fun contactHasPhoneSlotAfterEmailAndHeaderGrows() {
        val p = project()
        val slots = ContentSlots.extract(p)
        val kinds = slots.map { it.kind }
        // قد تحوي أكثر من شاشة رأس ملف شخصي، فلكل بريد سطر هاتف واحد يليه مباشرة.
        assertTrue("no phone: $kinds", kinds.contains(SlotKind.PHONE))
        assertEquals("phone/email counts: $kinds", kinds.count { it == SlotKind.EMAIL }, kinds.count { it == SlotKind.PHONE })
        kinds.forEachIndexed { i, k -> if (k == SlotKind.EMAIL) assertEquals("order: $kinds", SlotKind.PHONE, kinds[i + 1]) }
        val q = ContentSlots.apply(p, mapOf(slots.first { it.kind == SlotKind.PHONE }.elementId to "+962 79 123 4567"))
        val phone = q.screens.flatMap { ElementTree.flatten(it.elements) }.first { it.properties[ContentSlots.SLOT_KEY] == ContentSlots.SLOT_PHONE }
        assertEquals("apply phone", "+962 79 123 4567", phone.properties[PropertyKeys.TEXT])
        // ids unique
        assertEquals("ids unique", ids(p).size, ids(p).toSet().size)
    }

    @Test fun pendingStoreCodec() {
        val raw = com.arabicuieditor.data.planner.SetupStore.encode(listOf("a", "b", "a"))
        assertEquals(listOf("a", "b"), com.arabicuieditor.data.planner.SetupStore.decode(raw))
        assertEquals(emptyList<String>(), com.arabicuieditor.data.planner.SetupStore.decode(null))
    }

    @Test fun oldJsonWithoutSlotTagsStillLoadsAndExtractsNothingBroken() {
        val p = project()
        val old = ProjectJsonCodec.encode(p).replace("\"slot\"", "\"legacy_slot\"")
        val back = ProjectJsonCodec.decode(old)
        assertEquals(p.id, back.id)
        assertEquals(p.screens.size, back.screens.size)
        assertEquals(ids(p), ids(back))
        // لا يوجد وسم، لكن النصوص تبقى قابلة للاستخراج والتطبيق دون انهيار.
        assertEquals(back, ContentSlots.apply(back, emptyMap()))
    }

    @Test fun savedFilledProjectReloadsIdentically() {
        val dir = java.io.File(System.getProperty("java.io.tmpdir"), "slots-" + System.nanoTime()).apply { mkdirs() }
        try {
            val repo = com.arabicuieditor.data.storage.ProjectRepository(dir)
            val p = project(PlanKind.SHOP)
            val values = ContentSlots.extract(p).filter { it.kind == SlotKind.TEXT }.take(2).associate { it.elementId to "قيمة" }
            val filled = ContentSlots.apply(p, values)
            repo.save(filled)
            val loaded = repo.load(filled.id)!!
            assertEquals(filled.id, loaded.id)
            assertEquals(ids(filled), ids(loaded))
            assertEquals(1, repo.list().count { it.id == filled.id })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun filledImageIsBundledInExport() {
        val p = project(PlanKind.SHOP)
        val img = ContentSlots.extract(p).first { it.kind == SlotKind.IMAGE }
        val q = ContentSlots.apply(p, mapOf(img.elementId to "img_1.png"))
        val bytes = byteArrayOf(1, 2, 3)
        val f = com.arabicuieditor.data.export.AndroidProjectExporter.files(q, "com.example.app", mapOf("img_1.png" to bytes))
        assertTrue(f.containsKey("app/src/main/res/drawable/img_1.png"))
        assertEquals(bytes.toList(), f.getValue("app/src/main/res/drawable/img_1.png").toList())
        // بدون الصورة: يُذكر الاسم في README بدل أن يفشل التصدير
        val g = com.arabicuieditor.data.export.AndroidProjectExporter.files(q, "com.example.app", emptyMap())
        assertTrue(String(g.getValue("README.md")).contains("img_1.png"))
    }

    @Test fun whatsappAndAddressSlotsFollowPhoneAndValidate() {
        val p = project(PlanKind.CLINIC)
        val kinds = ContentSlots.extract(p).map { it.kind }
        val i = kinds.indexOf(SlotKind.PHONE)
        assertTrue(i >= 0)
        assertEquals(SlotKind.WHATSAPP, kinds[i + 1])
        assertEquals(SlotKind.ADDRESS, kinds[i + 2])
        assertTrue(ContentSlots.isValid(SlotKind.WHATSAPP, "+962791234567"))
        assertFalse(ContentSlots.isValid(SlotKind.WHATSAPP, "واتساب"))
        assertTrue(ContentSlots.isValid(SlotKind.ADDRESS, "عمّان، شارع الجامعة"))
        assertFalse(ContentSlots.isValid(SlotKind.ADDRESS, "  "))
        val slots = ContentSlots.extract(p)
        val q = ContentSlots.apply(p, mapOf(slots.first { it.kind == SlotKind.ADDRESS }.elementId to "عمّان"))
        val addr = q.screens.flatMap { com.arabicuieditor.data.model.ElementTree.flatten(it.elements) }
            .first { it.properties[ContentSlots.SLOT_KEY] == ContentSlots.SLOT_ADDRESS }
        assertEquals("عمّان", addr.properties[com.arabicuieditor.data.model.PropertyKeys.TEXT])
    }
}
