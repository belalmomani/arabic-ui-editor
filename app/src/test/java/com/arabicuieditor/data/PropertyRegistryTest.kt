package com.arabicuieditor.data

import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ColorUtil
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.PropertyContext
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SCREEN_TYPE
import com.arabicuieditor.data.model.PropertyKind
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.boolProp
import com.arabicuieditor.data.model.colorProp
import com.arabicuieditor.data.model.intProp
import com.arabicuieditor.data.model.prop
import com.arabicuieditor.data.model.withProp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyRegistryTest {

    @Test
    fun keysAreUnique() {
        val keys = PropertyRegistry.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun everyDefaultValueIsValidForItsKind() {
        // يضمن ألا يخرج أي افتراضي عن قواعد التحقق نفسها (مصدر واحد للحقيقة).
        for (def in PropertyRegistry.all) {
            for (type in def.appliesTo) {
                val default = def.defaultFor(type)
                if (def.kind is PropertyKind.Text) continue // النص يقبل أي قيمة
                assertNotNull("default of ${def.key} for $type = '$default' is invalid", PropertyRegistry.normalize(def.key, default))
            }
        }
    }

    @Test
    fun integersAreClampedAndGarbageRejected() {
        assertEquals("100", PropertyRegistry.normalize(PropertyKeys.OPACITY, "250"))
        assertEquals("0", PropertyRegistry.normalize(PropertyKeys.OPACITY, "-5"))
        assertEquals("14", PropertyRegistry.normalize(PropertyKeys.FONT_SIZE, " 14 "))
        assertNull(PropertyRegistry.normalize(PropertyKeys.FONT_SIZE, "abc"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.FONT_SIZE, ""))
    }

    @Test
    fun colorsAreNormalizedToArgb() {
        assertEquals("#FFFF0000", PropertyRegistry.normalize(PropertyKeys.BG_COLOR, "#ff0000"))
        assertEquals("#80112233", PropertyRegistry.normalize(PropertyKeys.BG_COLOR, "#80112233"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.BG_COLOR, "red"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.BG_COLOR, "#12345"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.BG_COLOR, "#GGGGGG"))
    }

    @Test
    fun choicesAndTogglesAreValidated() {
        assertEquals("bold", PropertyRegistry.normalize(PropertyKeys.FONT_WEIGHT, "bold"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.FONT_WEIGHT, "heavy"))
        assertEquals("false", PropertyRegistry.normalize(PropertyKeys.VISIBLE, "false"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.VISIBLE, "yes"))
        assertNull(PropertyRegistry.normalize("unknownKey", "x"))
    }

    @Test
    fun colorUtilRoundTrips() {
        val argb = ColorUtil.parse("#FF1B5E8C")!!
        assertEquals("#FF1B5E8C", ColorUtil.format(argb))
        assertEquals(0x00000000, ColorUtil.parse("#00000000"))
        assertEquals("#FFFFFFFF", ColorUtil.format(ColorUtil.parse("#FFFFFF")!!))
    }

    @Test
    fun applicabilityMatchesElementKind() {
        val image = PropertyRegistry.forType(ElementTypes.IMAGE).map { it.key }
        assertFalse(PropertyKeys.TEXT in image)
        assertFalse(PropertyKeys.FONT_SIZE in image)
        assertTrue(PropertyKeys.CORNER_RADIUS in image)

        val input = PropertyRegistry.forType(ElementTypes.INPUT).map { it.key }
        assertTrue(PropertyKeys.HINT in input)
        assertFalse(PropertyKeys.TEXT in input)

        val list = PropertyRegistry.forType(ElementTypes.LIST).map { it.key }
        assertTrue(PropertyKeys.ITEMS in list)

        // كل عنصر يملك الخصائص العامة
        for (type in ElementTypes.all) {
            val keys = PropertyRegistry.forType(type).map { it.key }
            assertTrue("$type has name", PropertyKeys.NAME in keys)
            assertTrue("$type has visible", PropertyKeys.VISIBLE in keys)
            assertTrue("$type has opacity", PropertyKeys.OPACITY in keys)
        }
    }

    @Test
    fun accessorsFallBackToTypeDefaultsAndPrefersStoredValue() {
        val button = UiElement(id = "b1", type = ElementTypes.BUTTON)
        assertEquals(14, button.intProp(PropertyKeys.FONT_SIZE))
        assertEquals(8, button.intProp(PropertyKeys.CORNER_RADIUS))
        assertEquals(ColorUtil.parse("#FF1B5E8C"), button.colorProp(PropertyKeys.BG_COLOR))
        assertTrue(button.boolProp(PropertyKeys.VISIBLE))
        assertEquals("center", button.prop(PropertyKeys.TEXT_ALIGN))

        val changed = button.withProp(PropertyKeys.FONT_SIZE, "22").withProp(PropertyKeys.VISIBLE, "false")
        assertEquals(22, changed.intProp(PropertyKeys.FONT_SIZE))
        assertFalse(changed.boolProp(PropertyKeys.VISIBLE))

        // عناصر المرحلة 3 (خصائص text فقط) تظهر بالمظهر الافتراضي السابق
        val legacyText = UiElement(id = "t", type = ElementTypes.TEXT, properties = mapOf("text" to "مرحبا"))
        assertEquals("مرحبا", legacyText.prop(PropertyKeys.TEXT))
        assertEquals(0, legacyText.intProp(PropertyKeys.BORDER_WIDTH))
    }

    private fun keysIn(type: String, own: ChildLayout, parent: ChildLayout, isScreen: Boolean = false) =
        PropertyRegistry.forContext(type, PropertyContext(own, parent, isScreen)).map { it.key }

    @Test
    fun layoutPropertiesAppearOnlyWhereTheyMakeSense() {
        // عنصر حر: خيارات التثبيت، بلا وزن ولا محاذاة ذاتية
        val free = keysIn(ElementTypes.BUTTON, ChildLayout.ABSOLUTE, ChildLayout.ABSOLUTE)
        assertTrue(PropertyKeys.PIN_H in free && PropertyKeys.PIN_V in free)
        assertFalse(PropertyKeys.WEIGHT in free)
        assertFalse(PropertyKeys.SELF_ALIGN_H in free)

        // داخل عمود: وزن ومحاذاة أفقية ذاتية، بلا تثبيت ولا محاذاة رأسية
        val inColumn = keysIn(ElementTypes.BUTTON, ChildLayout.ABSOLUTE, ChildLayout.COLUMN)
        assertTrue(PropertyKeys.WEIGHT in inColumn && PropertyKeys.SELF_ALIGN_H in inColumn)
        assertFalse(PropertyKeys.SELF_ALIGN_V in inColumn)
        assertFalse(PropertyKeys.PIN_H in inColumn)

        // داخل صف: العكس
        val inRow = keysIn(ElementTypes.BUTTON, ChildLayout.ABSOLUTE, ChildLayout.ROW)
        assertTrue(PropertyKeys.SELF_ALIGN_V in inRow && !(PropertyKeys.SELF_ALIGN_H in inRow))

        // داخل صندوق: محاذاتان ذاتيتان بلا وزن
        val inBox = keysIn(ElementTypes.BUTTON, ChildLayout.ABSOLUTE, ChildLayout.BOX)
        assertTrue(PropertyKeys.SELF_ALIGN_H in inBox && PropertyKeys.SELF_ALIGN_V in inBox)
        assertFalse(PropertyKeys.WEIGHT in inBox)
    }

    @Test
    fun containerLayoutPropertiesFollowItsChildLayout() {
        val free = keysIn(ElementTypes.CONTAINER, ChildLayout.ABSOLUTE, ChildLayout.ABSOLUTE)
        assertFalse(PropertyKeys.ARRANGEMENT in free || PropertyKeys.SPACING in free)

        val column = keysIn(ElementTypes.CONTAINER, ChildLayout.COLUMN, ChildLayout.ABSOLUTE)
        assertTrue(PropertyKeys.ARRANGEMENT in column && PropertyKeys.SPACING in column && PropertyKeys.CHILD_ALIGN_H in column)
        assertFalse(PropertyKeys.CHILD_ALIGN_V in column)

        val row = keysIn(ElementTypes.CONTAINER, ChildLayout.ROW, ChildLayout.ABSOLUTE)
        assertTrue(PropertyKeys.CHILD_ALIGN_V in row && !(PropertyKeys.CHILD_ALIGN_H in row))

        val box = keysIn(ElementTypes.CONTAINER, ChildLayout.BOX, ChildLayout.ABSOLUTE)
        assertTrue(PropertyKeys.CHILD_ALIGN_H in box && PropertyKeys.CHILD_ALIGN_V in box)
        assertFalse(PropertyKeys.SPACING in box)

        // الأزرار ليست حاويات
        assertFalse(PropertyKeys.ARRANGEMENT in keysIn(ElementTypes.BUTTON, ChildLayout.COLUMN, ChildLayout.ABSOLUTE))
    }

    @Test
    fun screenRootUsesTheSameRegistry() {
        val free = keysIn(SCREEN_TYPE, ChildLayout.ABSOLUTE, ChildLayout.ABSOLUTE, isScreen = true)
        assertTrue(free.isEmpty())
        val column = keysIn(SCREEN_TYPE, ChildLayout.COLUMN, ChildLayout.ABSOLUTE, isScreen = true)
        assertTrue(PropertyKeys.SPACING in column && PropertyKeys.PADDING_START in column)
        assertFalse(PropertyKeys.NAME in column || PropertyKeys.MARGIN_START in column)
        // القيم تُتحقق بالقواعد نفسها
        assertEquals("center", PropertyRegistry.normalize(PropertyKeys.ARRANGEMENT, "center"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.ARRANGEMENT, "diagonal"))
        assertEquals("10", PropertyRegistry.normalize(PropertyKeys.WEIGHT, "99"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.PIN_H, "top"))
    }
}
