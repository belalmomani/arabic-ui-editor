package com.arabicuieditor.data

import com.arabicuieditor.data.export.ComposeExporter
import com.arabicuieditor.data.model.ButtonStyles
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.PressEffects
import com.arabicuieditor.data.model.SCREEN_TYPE
import com.arabicuieditor.data.model.ScreenTransitions
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.newProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonStylesTest {
    private fun codeFor(style: String): String {
        val btn = UiElement(
            id = "b", type = ElementTypes.BUTTON,
            layout = ElementLayout(0f, 0f, SizeSpec.fixed(100f), SizeSpec.fixed(40f)),
            properties = mapOf(PropertyKeys.TEXT to "زر", PropertyKeys.BUTTON_STYLE to style)
        )
        val p = newProject("م", "الرئيسية")
        return ComposeExporter.export(p.copy(screens = listOf(p.screens[0].copy(elements = listOf(btn)))))
    }

    @Test
    fun styleIsButtonOnlyAndDefaultsToFlat() {
        assertEquals(ButtonStyles.FLAT, PropertyRegistry.defaultOf(ElementTypes.BUTTON, PropertyKeys.BUTTON_STYLE))
        val keys = PropertyRegistry.forType(ElementTypes.TEXT).map { it.key }
        assertFalse(keys.contains(PropertyKeys.BUTTON_STYLE))
        assertTrue(PropertyRegistry.forType(ElementTypes.BUTTON).map { it.key }.contains(PropertyKeys.BUTTON_STYLE))
    }

    @Test
    fun everyListedStyleExceptFlatHasALook() {
        assertNull(ButtonStyles.look(ButtonStyles.FLAT))
        ButtonStyles.options.filter { it.first != ButtonStyles.FLAT }.forEach {
            assertNotNull(it.first, ButtonStyles.look(it.first))
        }
    }

    @Test
    fun gradientExportsABrush() {
        val code = codeFor(ButtonStyles.GRADIENT)
        assertTrue(code.contains("Brush.verticalGradient"))
        assertTrue(code.contains("lerp("))
    }

    @Test
    fun neonAndGlowExportColoredShadow() {
        assertTrue(codeFor(ButtonStyles.NEON).contains("ambientColor"))
        assertTrue(codeFor(ButtonStyles.GLOW).contains("spotColor"))
    }

    @Test
    fun flatExportsNoBrush() {
        assertFalse(codeFor(ButtonStyles.FLAT).contains("Brush.verticalGradient"))
    }

    private fun codeWithPress(effect: String): String {
        val btn = UiElement(
            id = "b", type = ElementTypes.BUTTON,
            layout = ElementLayout(0f, 0f, SizeSpec.fixed(100f), SizeSpec.fixed(40f)),
            properties = mapOf(PropertyKeys.TEXT to "زر", PropertyKeys.PRESS_EFFECT to effect)
        )
        val p = newProject("م", "الرئيسية")
        return ComposeExporter.export(p.copy(screens = listOf(p.screens[0].copy(elements = listOf(btn)))))
    }

    @Test
    fun pressEffectDefaultsToNoneAndExportsHelperOnlyWhenUsed() {
        assertEquals(PressEffects.NONE, PropertyRegistry.defaultOf(ElementTypes.BUTTON, PropertyKeys.PRESS_EFFECT))
        val none = codeWithPress(PressEffects.NONE)
        assertFalse(none.contains("pressEffect"))
        val shrink = codeWithPress(PressEffects.SHRINK)
        assertTrue(shrink.contains(".pressEffect(\"shrink\")"))
        assertTrue(shrink.contains("private fun Modifier.pressEffect"))
        assertTrue(shrink.contains("import androidx.compose.foundation.gestures.awaitEachGesture"))
    }

    @Test
    fun pressScaleAndAlphaValues() {
        assertEquals(0.94f, PressEffects.scaleOf(PressEffects.SHRINK), 0f)
        assertEquals(1.06f, PressEffects.scaleOf(PressEffects.GROW), 0f)
        assertEquals(0.7f, PressEffects.alphaOf(PressEffects.DIM), 0f)
        assertEquals(1f, PressEffects.scaleOf(PressEffects.NONE), 0f)
    }

    @Test
    fun screenTransitionDefaultsToNoneAndExportsAnimatedContent() {
        assertEquals(ScreenTransitions.NONE, PropertyRegistry.defaultOf(SCREEN_TYPE, PropertyKeys.SCREEN_TRANSITION))
        val p = newProject("م", "الرئيسية")
        val s2 = p.screens[0].copy(id = "s2", name = "الثانية", properties = mapOf(PropertyKeys.SCREEN_TRANSITION to ScreenTransitions.SLIDE))
        val withAnim = ComposeExporter.export(p.copy(screens = listOf(p.screens[0], s2)))
        assertTrue(withAnim.contains("AnimatedContent("))
        assertTrue(withAnim.contains("\"screen2\" -> (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()"))
        assertTrue(withAnim.contains("import androidx.compose.animation.*"))
        val plain = ComposeExporter.export(p.copy(screens = listOf(p.screens[0], p.screens[0].copy(id = "s2", name = "ب"))))
        assertFalse(plain.contains("AnimatedContent"))
    }

    @Test
    fun pivotDefaultsToCenterAndExportsOriginOnlyWhenCustom() {
        assertEquals("50", PropertyRegistry.defaultOf(ElementTypes.BUTTON, PropertyKeys.PIVOT_X))
        fun code(px: String): String {
            val btn = UiElement(
                id = "b", type = ElementTypes.BUTTON,
                layout = ElementLayout(0f, 0f, SizeSpec.fixed(100f), SizeSpec.fixed(40f)),
                properties = mapOf(PropertyKeys.ROTATION to "30", PropertyKeys.PIVOT_X to px, PropertyKeys.PIVOT_Y to "0")
            )
            val p = newProject("م", "الرئيسية")
            return ComposeExporter.export(p.copy(screens = listOf(p.screens[0].copy(elements = listOf(btn)))))
        }
        assertTrue(code("0").contains("transformOrigin = TransformOrigin(0.0f, 0.0f)"))
        assertTrue(code("50").contains("transformOrigin = TransformOrigin(0.5f, 0.0f)"))
    }
}
