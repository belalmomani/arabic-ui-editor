package com.arabicuieditor.data

import com.arabicuieditor.data.export.FlutterExporter
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.newProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlutterExporterTest {

    private fun el(
        id: String, type: String, w: Float = 100f, h: Float = 40f, x: Float = 0f, y: Float = 0f,
        props: Map<String, String> = emptyMap(), kids: List<UiElement> = emptyList(),
        layout: ChildLayout = ChildLayout.ABSOLUTE
    ) = UiElement(
        id = id, type = type,
        layout = ElementLayout(x, y, SizeSpec(SizeMode.FIXED, w), SizeSpec(SizeMode.FIXED, h)),
        childLayout = layout, properties = props, children = kids
    )

    private fun project(vararg screens: UiScreen): Project =
        newProject("تجربة", "الأولى").copy(screens = screens.toList(), startScreenId = screens.first().id)

    /** توازن الأقواس خارج النصوص المحاطة بـ '...' (يتجاهل \' المهربة). */
    private fun assertBalanced(src: String) {
        var depthP = 0; var depthB = 0; var depthC = 0
        var inStr = false
        var i = 0
        while (i < src.length) {
            val c = src[i]
            if (inStr) {
                if (c == '\\') i++ else if (c == '\'') inStr = false
            } else when (c) {
                '\'' -> inStr = true
                '(' -> depthP++
                ')' -> depthP--
                '[' -> depthB++
                ']' -> depthB--
                '{' -> depthC++
                '}' -> depthC--
            }
            assertTrue("negative depth at $i", depthP >= 0 && depthB >= 0 && depthC >= 0)
            i++
        }
        assertEquals(0, depthP); assertEquals(0, depthB); assertEquals(0, depthC)
    }

    @Test
    fun routesAndNavigation() {
        val b = Interactions.withClickAction(el("b", ElementTypes.BUTTON, props = mapOf(PropertyKeys.TEXT to "اذهب")), ClickAction.Navigate("s2"))
        val s1 = UiScreen("s1", "أ", 360f, 640f, listOf(b))
        val s2 = UiScreen("s2", "ب", 360f, 640f, listOf(Interactions.withClickAction(el("c", ElementTypes.BUTTON), ClickAction.Back)))
        val out = FlutterExporter.export(project(s1, s2))
        assertBalanced(out)
        assertTrue(out.contains("'/screen2': (context) => const Screen2()"))
        assertTrue(out.contains("Navigator.pushNamed(context, '/screen2')"))
        assertTrue(out.contains("Navigator.pop(context)"))
        assertTrue(out.contains("initialRoute: '/screen1'"))
    }

    @Test
    fun mixedContentIsBalancedAndEscaped() {
        val card = el("c", ElementTypes.CARD, 200f, 120f, props = mapOf(PropertyKeys.CORNER_RADIUS to "12"),
            layout = ChildLayout.COLUMN, kids = listOf(
                el("t", ElementTypes.TEXT, props = mapOf(PropertyKeys.TEXT to "it's \$5\nسطر")),
                el("i", ElementTypes.INPUT, props = mapOf(PropertyKeys.HINT to "اسمك"))
            ))
        val s = UiScreen("s1", "الرئيسية", 360f, 640f, listOf(
            card,
            el("img", ElementTypes.IMAGE, props = mapOf(PropertyKeys.IMAGE_SRC to "img_1.png")),
            el("ico", ElementTypes.IMAGE, props = mapOf(PropertyKeys.ICON to "ShoppingCart")),
            el("l", ElementTypes.LIST, props = mapOf(PropertyKeys.ITEMS to "أ\nب")),
            el("r", ElementTypes.CONTAINER, layout = ChildLayout.ROW, props = mapOf(PropertyKeys.ROTATION to "10"))
        ), childLayout = ChildLayout.COLUMN)
        val out = FlutterExporter.export(project(s))
        assertBalanced(out)
        assertTrue(out.contains("it\\'s \\\$5\\nسطر"))
        assertTrue(out.contains("Image.asset('assets/img_1.png'"))
        assertTrue(out.contains("Icons.shopping_cart"))
        assertTrue(out.contains("import 'dart:math' as math;"))
        assertTrue(out.contains("BoxShadow"))
    }

    @Test
    fun absoluteUsesPositioned() {
        val s = UiScreen("s1", "أ", 360f, 640f, listOf(el("t", ElementTypes.TEXT, x = 20f, y = 30f)))
        val out = FlutterExporter.export(project(s))
        assertBalanced(out)
        assertTrue(out.contains("left: 20.0"))
        assertTrue(out.contains("top: 30.0"))
    }

    @Test
    fun onlyOneScreenHasNoApp() {
        val s1 = UiScreen("s1", "أ", 360f, 640f, emptyList())
        val s2 = UiScreen("s2", "ب", 360f, 640f, emptyList())
        val out = FlutterExporter.export(project(s1, s2), "s2")
        assertBalanced(out)
        assertTrue(!out.contains("MaterialApp"))
        assertTrue(out.contains("class Screen2"))
    }
}
