package com.arabicuieditor.data

import com.arabicuieditor.data.model.ColorUtil
import com.arabicuieditor.data.model.DEFAULT_COLORS
import com.arabicuieditor.data.model.DesignSystem
import com.arabicuieditor.data.model.DesignThemes
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.Tokens
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.colorProp
import com.arabicuieditor.data.model.intProp
import com.arabicuieditor.data.model.newElement
import com.arabicuieditor.data.model.prop
import com.arabicuieditor.data.model.resolved
import com.arabicuieditor.data.model.withProp
import com.arabicuieditor.data.storage.ProjectJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** نظام التصميم: المراجع، والحل، والسمات، والحفظ والاستعادة. */
class DesignSystemTest {

    private fun button() = newElement(ElementTypes.BUTTON, 0f, 0f, "زر")

    @Test
    fun defaultsKeepTheOriginalLook() {
        val b = button()
        assertEquals(ColorUtil.parse("#FF1B5E8C"), b.colorProp(PropertyKeys.BG_COLOR))
        assertEquals(ColorUtil.parse("#FFFFFFFF"), b.colorProp(PropertyKeys.TEXT_COLOR))
        assertEquals(8, b.intProp(PropertyKeys.CORNER_RADIUS))
        assertEquals(14, b.intProp(PropertyKeys.FONT_SIZE))
        assertEquals(12, newElement(ElementTypes.CARD, 0f, 0f, "").intProp(PropertyKeys.CORNER_RADIUS))
    }

    @Test
    fun normalizeAcceptsOnlyValidReferencesForTheRightProperty() {
        assertEquals("@color/primary", PropertyRegistry.normalize(PropertyKeys.BG_COLOR, "@color/primary"))
        assertEquals("@radius/large", PropertyRegistry.normalize(PropertyKeys.CORNER_RADIUS, "@radius/large"))
        assertEquals("@text/title", PropertyRegistry.normalize(PropertyKeys.FONT_SIZE, "@text/title"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.BG_COLOR, "@color/nope"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.BG_COLOR, "@radius/small"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.FONT_SIZE, "@radius/small"))
        assertNull(PropertyRegistry.normalize(PropertyKeys.OPACITY, "@text/body"))
    }

    @Test
    fun changingThePrimaryColorChangesEveryLinkedElement() {
        val ds = DesignSystem.DEFAULT.withColor(Tokens.PRIMARY, 0xFFE8710A.toInt())
        val linked = button()
        val fixed = button().withProp(PropertyKeys.BG_COLOR, "#FF00FF00")
        assertEquals(0xFFE8710A.toInt(), ColorUtil.parse(linked.resolved(ds).prop(PropertyKeys.BG_COLOR)))
        // اللون المباشر مستقل عن النظام.
        assertEquals("#FF00FF00", fixed.resolved(ds).prop(PropertyKeys.BG_COLOR))
        // وتبقى النسخة الأصلية مرجعًا لا قيمة.
        assertEquals("@color/primary", linked.prop(PropertyKeys.BG_COLOR))
    }

    @Test
    fun radiusAndTextSizeFollowTheSystem() {
        val ds = DesignSystem.DEFAULT.withRadius(Tokens.MEDIUM, 20).withTextSize(Tokens.BODY, 18)
        val r = button().resolved(ds)
        assertEquals(20, r.intProp(PropertyKeys.CORNER_RADIUS))
        assertEquals(18, r.intProp(PropertyKeys.FONT_SIZE))
        val own = button().withProp(PropertyKeys.CORNER_RADIUS, "3").resolved(ds)
        assertEquals(3, own.intProp(PropertyKeys.CORNER_RADIUS))
    }

    @Test
    fun themesAreCompleteAndDistinct() {
        val ids = DesignThemes.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (t in DesignThemes.all) {
            assertEquals(t.id, DEFAULT_COLORS.keys, t.colors.keys)
        }
        assertEquals(DEFAULT_COLORS, DesignThemes.all.first { it.id == "ocean" }.colors)
        val night = DesignThemes.all.first { it.id == "night" }.colors
        assertNotEquals(DEFAULT_COLORS.getValue(Tokens.BACKGROUND), night.getValue(Tokens.BACKGROUND))
    }

    @Test
    fun designSystemSurvivesSaveAndReopen() {
        val ds = DesignSystem.DEFAULT
            .withColor(Tokens.PRIMARY, 0xFF2E7D32.toInt())
            .withRadius(Tokens.LARGE, 24)
            .withTextSize(Tokens.HEADING, 30)
        val project = Project(
            id = "abcdefgh-9", name = "ن", createdAt = 1, updatedAt = 2,
            screens = listOf(UiScreen("s1", "ش", elements = listOf(button()))),
            designSystem = ds
        )
        val back = ProjectJsonCodec.decode(ProjectJsonCodec.encode(project))
        assertEquals(ds, back.designSystem)
        assertEquals("@color/primary", back.screens[0].elements[0].prop(PropertyKeys.BG_COLOR))
    }

    @Test
    fun oldAndPartialFilesFallBackToDefaults() {
        val old = """{"schemaVersion":3,"id":"abcdefgh-3","name":"قديم","createdAt":1,"updatedAt":2,"screens":[],"resources":[]}"""
        assertEquals(DesignSystem.DEFAULT, ProjectJsonCodec.decode(old).designSystem)

        val partial = """{"schemaVersion":4,"id":"abcdefgh-4","name":"ن","createdAt":1,"updatedAt":2,"screens":[],"resources":[],
            "designSystem":{"colors":{"primary":"#FF112233","danger":"not-a-color","unknown":"#FF000000"},"radii":{"small":500}}}"""
        val ds = ProjectJsonCodec.decode(partial).designSystem
        assertEquals(0xFF112233.toInt(), ds.color(Tokens.PRIMARY))
        assertEquals(DEFAULT_COLORS.getValue(Tokens.DANGER), ds.color(Tokens.DANGER))
        assertEquals(100, ds.radius(Tokens.SMALL))
        assertEquals(DesignSystem.DEFAULT.radius(Tokens.MEDIUM), ds.radius(Tokens.MEDIUM))
        assertTrue(ds.textSizes.keys.containsAll(Tokens.textNames))
    }
}
