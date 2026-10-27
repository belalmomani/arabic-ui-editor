package com.arabicuieditor.data

import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ComponentLibrary
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.PresetCategory
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentLibraryTest {

    @Test
    fun everyCategoryHasPresets() {
        PresetCategory.entries.forEach { c ->
            assertTrue("$c is empty", ComponentLibrary.byCategory(c).isNotEmpty())
        }
    }

    @Test
    fun presetIdsAreUnique() {
        val ids = ComponentLibrary.presets.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun presetsUseOnlyRegisteredValidProperties() {
        // المكتبة تُبنى من الخصائص نفسها: لا مفتاح مجهول ولا قيمة غير صالحة ولا خاصية لا تنطبق على النوع.
        for (preset in ComponentLibrary.presets) {
            val built = preset.build { "نص" }
            assertTrue("${preset.id} builds nothing", built.isNotEmpty())
            val all = ElementTree.flatten(built)
            assertEquals("ids not unique in ${preset.id}", all.size, all.map { it.id }.toSet().size)
            for (e in all) {
                val allowed = PropertyRegistry.forType(e.type).map { it.key }.toSet()
                for ((key, value) in e.properties) {
                    assertTrue("${preset.id}: '$key' does not apply to ${e.type}", key in allowed)
                    assertNotNull("${preset.id}: invalid $key='$value'", PropertyRegistry.normalize(key, value))
                }
                assertTrue(e.layout.width.value > 0f && e.layout.height.value > 0f)
            }
        }
    }

    @Test
    fun layoutPresetsBuildNestedFlexibleContainers() {
        val presets = ComponentLibrary.byCategory(PresetCategory.LAYOUTS)
        assertTrue(presets.size >= 3)
        for (preset in presets) {
            val root = preset.build { "نص" }.single()
            assertTrue("${preset.id} has no children", root.children.isNotEmpty())
            assertTrue("${preset.id} is not flexible", root.childLayout != ChildLayout.ABSOLUTE)
            // وتُحسب إطاراتها كاملة بلا مشاكل
            val frames = LayoutEngine.layout(UiScreen("s", "ش", elements = listOf(root))).frames
            assertEquals(ElementTree.count(listOf(root)), frames.size)
        }
        val kinds = presets.map { it.build { "x" }.single().childLayout }.toSet()
        assertTrue(kinds.containsAll(listOf(ChildLayout.ROW, ChildLayout.COLUMN, ChildLayout.BOX)))
    }

    @Test
    fun buildingTwiceGivesFreshIds() {
        val preset = ComponentLibrary.presets.first { it.id == "card_title_body" }
        val a = preset.build { "x" }.map { it.id }
        val b = preset.build { "x" }.map { it.id }
        assertTrue(a.intersect(b.toSet()).isEmpty())
    }

    @Test
    fun professionalComponentsAreNestedAndCoverTheRequestedKinds() {
        val pro = ComponentLibrary.presets.filter { it.id.startsWith("pro_") }
        val categories = pro.map { it.category }.toSet()
        for (c in listOf(
            PresetCategory.LOGIN, PresetCategory.PRODUCTS, PresetCategory.NAVIGATION, PresetCategory.MENUS,
            PresetCategory.DIALOGS, PresetCategory.NEWS, PresetCategory.CHAT
        )) assertTrue("$c missing", c in categories)
        for (preset in pro) {
            val built = preset.build { "x" }
            assertEquals("${preset.id} must be one root", 1, built.size)
            val all = ElementTree.flatten(built)
            // مكوّن مركّب: أكثر من عنصر، أو قائمة جاهزة.
            assertTrue("${preset.id} too small", all.size > 1 || built.single().type == ElementTypes.LIST)
        }
        // كل مكوّن يُحسب تخطيطه كاملًا وبأحجام موجبة ضمن عرض الشاشة.
        for (preset in pro) {
            val root = preset.build { "نص" }.single()
            val result = LayoutEngine.layout(UiScreen("s", "ش", elements = listOf(root)))
            assertEquals(preset.id, ElementTree.count(listOf(root)), result.frames.size)
            for ((id, f) in result.frames) {
                assertTrue("${preset.id}/$id has no size", f.w > 0f && f.h > 0f)
            }
            val rootFrame = result.frames.getValue(root.id)
            assertTrue("${preset.id} wider than the screen", rootFrame.w <= 360f)
        }
        // مكوّن مركّب يحمل أحفادًا متداخلين فعلًا (عمق 2 على الأقل).
        val card = ComponentLibrary.presets.first { it.id == "pro_product_pair" }.build { "x" }.single()
        assertTrue(card.children.all { it.children.isNotEmpty() })
    }
}
