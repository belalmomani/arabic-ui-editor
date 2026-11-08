package com.arabicuieditor.data

import com.arabicuieditor.data.export.XmlExporter
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
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xml.sax.InputSource

class XmlExporterTest {

    private fun el(
        id: String, type: String, w: Float = 100f, h: Float = 40f, x: Float = 0f, y: Float = 0f,
        props: Map<String, String> = emptyMap(), kids: List<UiElement> = emptyList(),
        layout: ChildLayout = ChildLayout.ABSOLUTE,
        wm: SizeMode = SizeMode.FIXED
    ) = UiElement(
        id = id, type = type,
        layout = ElementLayout(x, y, SizeSpec(wm, w), SizeSpec(SizeMode.FIXED, h)),
        childLayout = layout, properties = props, children = kids
    )

    private fun project(vararg screens: UiScreen): Project =
        newProject("تجربة", "الأولى").copy(screens = screens.toList(), startScreenId = screens.first().id)

    /** يقسم الناتج إلى ملفات ويتحقق أن كل ملف XML سليم الصياغة. يعيد الملفات بمساراتها. */
    private fun files(text: String): Map<String, String> {
        val parts = Regex("<!-- ===== (.+?) ===== -->\n").split(text).drop(1)
        val names = Regex("<!-- ===== (.+?) ===== -->").findAll(text).map { it.groupValues[1].substringBefore("  (") }.toList()
        assertEquals(names.size, parts.size)
        val map = names.zip(parts).toMap()
        for ((n, body) in map) {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(java.io.StringReader(body)))
        }
        return map
    }

    @Test
    fun wellFormedForMixedContent() {
        val card = el("c", ElementTypes.CARD, 200f, 120f, props = mapOf(PropertyKeys.CORNER_RADIUS to "12"),
            layout = ChildLayout.COLUMN, kids = listOf(
                el("t", ElementTypes.TEXT, props = mapOf(PropertyKeys.TEXT to "مرحبًا & <أهلًا> \"x\" 'y'")),
                el("b", ElementTypes.BUTTON, props = mapOf(PropertyKeys.TEXT to "زر", PropertyKeys.BORDER_WIDTH to "2"))
            ))
        val s = UiScreen("s1", "الرئيسية", 360f, 640f, listOf(
            card,
            el("i", ElementTypes.INPUT, props = mapOf(PropertyKeys.HINT to "اسمك")),
            el("img", ElementTypes.IMAGE, props = mapOf(PropertyKeys.IMAGE_SRC to "img_1.png")),
            el("ico", ElementTypes.IMAGE, props = mapOf(PropertyKeys.ICON to "ShoppingCart")),
            el("d", ElementTypes.DIVIDER, 100f, 1f),
            el("l", ElementTypes.LIST, props = mapOf(PropertyKeys.ITEMS to "أ\nب"))
        ), childLayout = ChildLayout.COLUMN)
        val out = XmlExporter.export(project(s))
        val f = files(out)
        assertTrue(f.keys.any { it == "res/layout/screen_1.xml" })
        assertTrue(f.keys.any { it.startsWith("res/drawable/bg_") })
        assertTrue(out.contains("androidx.cardview.widget.CardView"))
        assertTrue(out.contains("@drawable/img_1"))
        assertTrue(out.contains("ic_shopping_cart"))
        assertTrue(out.contains("&amp;"))
    }

    @Test
    fun absoluteChildrenGetMargins() {
        val s = UiScreen("s1", "أ", 360f, 640f, listOf(el("t", ElementTypes.TEXT, x = 20f, y = 30f)))
        val out = XmlExporter.export(project(s))
        files(out)
        assertTrue(out.contains("layout_marginStart=\"20dp\""))
        assertTrue(out.contains("layout_marginTop=\"30dp\""))
    }

    @Test
    fun actionsBecomeComments() {
        val b = Interactions.withClickAction(el("b", ElementTypes.BUTTON), ClickAction.Navigate("s2"))
        val s1 = UiScreen("s1", "أ", 360f, 640f, listOf(b))
        val s2 = UiScreen("s2", "ب", 360f, 640f, emptyList())
        val out = XmlExporter.export(project(s1, s2))
        files(out)
        assertTrue(out.contains("انتقال إلى screen_2"))
    }

    @Test
    fun onlyOneScreen() {
        val s1 = UiScreen("s1", "أ", 360f, 640f, emptyList())
        val s2 = UiScreen("s2", "ب", 360f, 640f, emptyList())
        val out = XmlExporter.export(project(s1, s2), "s2")
        val f = files(out)
        assertEquals(setOf("res/layout/screen_2.xml"), f.keys)
    }

    @Test
    fun flipExportsScaleAttributes() {
        val s = UiScreen("s1", "أ", 360f, 640f, listOf(
            el("t", ElementTypes.TEXT, props = mapOf(PropertyKeys.FLIP_X to "true", PropertyKeys.FLIP_Y to "true"))
        ))
        val out = XmlExporter.export(project(s))
        assertTrue(out.contains("android:scaleX=\"-1\""))
        assertTrue(out.contains("android:scaleY=\"-1\""))
    }
}
