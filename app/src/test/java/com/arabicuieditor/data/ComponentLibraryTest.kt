package com.arabicuieditor.data

import com.arabicuieditor.data.model.ComponentLibrary
import com.arabicuieditor.data.model.PresetCategory
import com.arabicuieditor.data.model.PropertyRegistry
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
            assertEquals("ids not unique in ${preset.id}", built.size, built.map { it.id }.toSet().size)
            for (e in built) {
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
    fun buildingTwiceGivesFreshIds() {
        val preset = ComponentLibrary.presets.first { it.id == "card_title_body" }
        val a = preset.build { "x" }.map { it.id }
        val b = preset.build { "x" }.map { it.id }
        assertTrue(a.intersect(b.toSet()).isEmpty())
    }
}
