package com.arabicuieditor.data

import com.arabicuieditor.data.export.ComposeExporter
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeExporterTest {

    private fun el(
        id: String, type: String, x: Float = 0f, y: Float = 0f, w: Float = 100f, h: Float = 40f,
        props: Map<String, String> = emptyMap(), children: List<UiElement> = emptyList(),
        layout: ChildLayout = ChildLayout.ABSOLUTE,
        wMode: SizeMode = SizeMode.FIXED, hMode: SizeMode = SizeMode.FIXED
    ) = UiElement(
        id = id, type = type,
        layout = ElementLayout(x, y, SizeSpec(wMode, w), SizeSpec(hMode, h)),
        childLayout = layout, properties = props, children = children
    )

    private fun project(vararg screens: UiScreen): Project =
        newProject("تجربة", "الأولى").copy(screens = screens.toList(), startScreenId = screens.first().id)

    private fun code(p: Project, only: String? = null) = ComposeExporter.export(p, only)

    /** فحص تركيبي بدائي: توازن الأقواس خارج السلاسل والتعليقات. لا يغني عن الترجمة الفعلية. */
    private fun assertBalanced(src: String) {
        var brace = 0; var paren = 0; var bracket = 0
        var i = 0
        var inString = false
        while (i < src.length) {
            val c = src[i]
            if (inString) {
                if (c == '\\') i++ else if (c == '"') inString = false
            } else when {
                c == '"' -> inString = true
                c == '/' && i + 1 < src.length && src[i + 1] == '/' -> {
                    while (i < src.length && src[i] != '\n') i++
                    continue
                }
                c == '/' && i + 1 < src.length && src[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < src.length && !(src[i] == '*' && src[i + 1] == '/')) i++
                    i++
                }
                c == '{' -> brace++
                c == '}' -> brace--
                c == '(' -> paren++
                c == ')' -> paren--
                c == '[' -> bracket++
                c == ']' -> bracket--
            }
            assertTrue("negative nesting at $i", brace >= 0 && paren >= 0 && bracket >= 0)
            i++
        }
        assertFalse("unterminated string", inString)
        assertEquals("braces", 0, brace)
        assertEquals("parens", 0, paren)
        assertEquals("brackets", 0, bracket)
    }

    @Test fun basicScreenStructure() {
        val p = project(UiScreen("s1", "الرئيسية", elements = listOf(
            el("t", ElementTypes.TEXT, 10f, 20f, 120f, 28f, mapOf(PropertyKeys.TEXT to "مرحبا")),
            el("b", ElementTypes.BUTTON, 10f, 60f, 120f, 44f, mapOf(PropertyKeys.TEXT to "ادخل"))
        )))
        val src = code(p)
        assertBalanced(src)
        assertTrue(src.contains("package com.example.app"))
        assertTrue(src.contains("fun Screen1("))
        assertTrue(src.contains("fun AppRoot()"))
        assertTrue(src.contains("text = \"مرحبا\""))
        assertTrue(src.contains(".offset(10.dp, 20.dp)"))
        assertTrue(src.contains(".size(120.dp, 28.dp)"))
        assertTrue(src.contains("LayoutDirection.Ltr"))
    }

    @Test fun tokensBecomeReferencesNotNumbers() {
        val p = project(UiScreen("s1", "أ", elements = listOf(
            el("b", ElementTypes.BUTTON, props = mapOf(PropertyKeys.TEXT to "x"))
        )))
        val src = code(p)
        assertTrue(src.contains("AppColors.primary"))
        assertTrue(src.contains("AppColors.onPrimary"))
        assertTrue(src.contains("AppRadius.medium"))
        assertTrue(src.contains("AppText.body"))
        assertFalse(src.contains("@color/"))
        assertFalse(src.contains("@radius/"))
        assertFalse(src.contains("@text/"))
        assertTrue(src.contains("object AppColors"))
        assertTrue(src.contains("val primary = Color(0xFF1B5E8C)"))
    }

    @Test fun literalColorsAndTransparent() {
        val p = project(UiScreen("s1", "أ", elements = listOf(
            el("c", ElementTypes.CONTAINER, props = mapOf(PropertyKeys.BG_COLOR to "#FFFF0000")),
            el("d", ElementTypes.CONTAINER, props = mapOf(PropertyKeys.BG_COLOR to "#00000000"))
        )))
        val src = code(p)
        assertTrue(src.contains(".background(Color(0xFFFF0000))"))
        assertEquals(1, Regex("\\.background\\(Color\\(0xFFFF0000\\)\\)").findAll(src).count())
        assertBalanced(src)
    }

    @Test fun columnWithWeightFillAndSpacing() {
        val col = el(
            "col", ElementTypes.CONTAINER, w = 100f, h = 100f, layout = ChildLayout.COLUMN,
            wMode = SizeMode.MATCH_PARENT, hMode = SizeMode.MATCH_PARENT,
            props = mapOf(PropertyKeys.SPACING to "8"),
            children = listOf(
                el("a", ElementTypes.TEXT, props = mapOf(PropertyKeys.WEIGHT to "2")),
                el("b", ElementTypes.TEXT, wMode = SizeMode.MATCH_PARENT)
            )
        )
        val src = code(project(UiScreen("s1", "أ", elements = listOf(col))))
        assertBalanced(src)
        assertTrue(src.contains("Column("))
        assertTrue(src.contains("verticalArrangement = Arrangement.spacedBy(8.dp)"))
        assertTrue(src.contains(".fillMaxWidth()"))
        assertTrue(src.contains(".weight(2f)"))
    }

    @Test fun rowAndBoxContainers() {
        val row = el("r", ElementTypes.CONTAINER, layout = ChildLayout.ROW,
            children = listOf(el("a", ElementTypes.TEXT)))
        val box = el("bx", ElementTypes.CARD, layout = ChildLayout.BOX,
            children = listOf(el("b", ElementTypes.TEXT)))
        val src = code(project(UiScreen("s1", "أ", elements = listOf(row, box))))
        assertBalanced(src)
        assertTrue(src.contains("Row("))
        assertTrue(src.contains("verticalAlignment = Alignment.Top"))
        assertTrue(src.contains("contentAlignment = Alignment.TopStart"))
        assertTrue(src.contains(".shadow(3.dp"))
    }

    @Test fun navigationAndBack() {
        val btn = Interactions.withClickAction(el("b", ElementTypes.BUTTON), ClickAction.Navigate("s2"))
        val back = Interactions.withClickAction(el("c", ElementTypes.BUTTON, y = 50f), ClickAction.Back)
        val dead = Interactions.withClickAction(el("d", ElementTypes.BUTTON, y = 100f), ClickAction.Navigate("gone"))
        val p = project(
            UiScreen("s1", "أ", elements = listOf(btn, back, dead)),
            UiScreen("s2", "ب")
        )
        val src = code(p)
        assertBalanced(src)
        assertTrue(src.contains(".clickable { onNavigate(\"screen2\") }"))
        assertTrue(src.contains(".clickable { onBack() }"))
        assertTrue(src.contains("TODO: الشاشة المستهدفة محذوفة"))
        assertTrue(src.contains("\"screen2\" -> Screen2("))
        assertTrue(src.contains("listOf(\"screen1\")"))
    }

    @Test fun hiddenElementsAreSkipped() {
        val p = project(UiScreen("s1", "أ", elements = listOf(
            el("t", ElementTypes.TEXT, props = mapOf(PropertyKeys.TEXT to "سري", PropertyKeys.VISIBLE to "false"))
        )))
        val src = code(p)
        assertFalse(src.contains("text = \"سري\""))
        assertTrue(src.contains("مخفي"))
    }

    @Test fun stringsAreEscaped() {
        val p = project(UiScreen("s1", "أ", elements = listOf(
            el("t", ElementTypes.TEXT, props = mapOf(PropertyKeys.TEXT to "قال \"مرحبا\" \$5\nسطر"))
        )))
        val src = code(p)
        assertTrue(src.contains("text = \"قال \\\"مرحبا\\\" \\\$5\\nسطر\""))
        assertBalanced(src)
    }

    @Test fun onlyOneScreenHasNoRoot() {
        val p = project(UiScreen("s1", "أ"), UiScreen("s2", "ب"))
        val src = code(p, only = "s2")
        assertTrue(src.contains("fun Screen2("))
        assertFalse(src.contains("fun Screen1("))
        assertFalse(src.contains("fun AppRoot()"))
        assertBalanced(src)
    }

    @Test fun inputListImageDividerSpacerAllEmit() {
        val p = project(UiScreen("s1", "أ", elements = listOf(
            el("i", ElementTypes.INPUT, props = mapOf(PropertyKeys.HINT to "البريد")),
            el("l", ElementTypes.LIST, props = mapOf(PropertyKeys.ITEMS to "أ\nب")),
            el("m", ElementTypes.IMAGE),
            el("d", ElementTypes.DIVIDER),
            el("s", ElementTypes.SPACER)
        )))
        val src = code(p)
        assertBalanced(src)
        assertTrue(src.contains("var input1 by remember"))
        assertTrue(src.contains("BasicTextField("))
        assertTrue(src.contains("listOf(\"أ\", \"ب\")"))
        assertTrue(src.contains("TODO: ضع صورتك"))
        assertTrue(src.contains("Spacer("))
    }

    @Test fun rotationAndOpacity() {
        val p = project(UiScreen("s1", "أ", elements = listOf(
            el("t", ElementTypes.TEXT, props = mapOf(PropertyKeys.ROTATION to "30", PropertyKeys.OPACITY to "50"))
        )))
        val src = code(p)
        assertTrue(src.contains(".rotate(30f)"))
        assertTrue(src.contains(".alpha(0.5f)"))
    }

    @Test fun deterministicOutput() {
        val p = project(UiScreen("s1", "أ", elements = listOf(el("t", ElementTypes.TEXT))))
        assertEquals(code(p), code(p))
    }
}
