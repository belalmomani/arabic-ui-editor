package com.arabicuieditor.data

import com.arabicuieditor.data.export.ComposeExporter
import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.newProject
import com.arabicuieditor.data.script.Step
import com.arabicuieditor.data.script.Steps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeLogicExportTest {
    private fun el(id: String, type: String, props: Map<String, String>, y: Float = 0f) = UiElement(
        id = id, type = type,
        layout = ElementLayout(0f, y, SizeSpec.fixed(100f), SizeSpec.fixed(40f)),
        properties = props
    )

    private fun project(steps: List<Step>, secondScreen: Boolean = false): com.arabicuieditor.data.model.Project {
        val label = el("l", ElementTypes.TEXT, mapOf(PropertyKeys.NAME to "عداد", PropertyKeys.TEXT to "العدد {{n}}"))
        val hidden = el("h", ElementTypes.TEXT, mapOf(PropertyKeys.NAME to "سر", PropertyKeys.TEXT to "x", PropertyKeys.VISIBLE to "false"), 50f)
        val btn = Interactions.withClickAction(
            el("b", ElementTypes.BUTTON, mapOf(PropertyKeys.NAME to "زر", PropertyKeys.TEXT to "اضغط"), 100f),
            ClickAction.Blocks(steps)
        )
        val p = newProject("م", "الرئيسية")
        val s1 = p.screens[0].copy(elements = listOf(label, hidden, btn))
        val screens = if (secondScreen) listOf(s1, p.screens[0].copy(id = "s2", name = "التفاصيل")) else listOf(s1)
        return p.copy(screens = screens, startScreenId = s1.id)
    }

    private val steps = listOf(
        Step(Steps.INC, "n", "2"),
        Step(Steps.IF, "n", ">=|3"), Step(Steps.SHOW, "سر"),
        Step(Steps.TEXT, "عداد", "تم \$x"),
        Step(Steps.COLOR, "عداد", "#E53935"),
        Step(Steps.BG, "عداد", "#43A047"),
        Step(Steps.TOGGLE, "سر"),
        Step(Steps.TOAST, "", "أهلا"),
        Step(Steps.NAV, "التفاصيل"),
        Step(Steps.BACK)
    )

    private fun count(s: String, c: Char) = s.count { it == c }

    @Test
    fun logicIsExportedAsKotlin() {
        val code = ComposeExporter.export(project(steps, secondScreen = true))
        assertTrue(code.contains("object AppLogic"))
        assertTrue(code.contains("AppLogic.add(\"n\", 2.0)"))
        assertTrue(code.contains("if (AppLogic.num(\"n\") >= 3.0) { AppLogic.props[\"سر|visible\"] = \"true\" }"))
        assertTrue(code.contains("AppLogic.props[\"عداد|text\"] = \"تم \\\$x\""))
        assertTrue(code.contains("AppLogic.props[\"عداد|textColor\"] = \"#E53935\""))
        assertTrue(code.contains("AppLogic.props[\"عداد|bgColor\"] = \"#43A047\""))
        assertTrue(code.contains("Toast.makeText(ctx, \"أهلا\", Toast.LENGTH_SHORT).show()"))
        assertTrue(code.contains("onNavigate(\"screen2\")"))
        assertTrue(code.contains("onBack()"))
        assertTrue(code.contains("import android.widget.Toast"))
        assertTrue(code.contains("val ctx = LocalContext.current"))
        assertFalse(code.contains("TODO: منطق"))
    }

    @Test
    fun targetsAreDynamicOthersStatic() {
        val code = ComposeExporter.export(project(steps))
        assertTrue(code.contains("AppLogic.interp(AppLogic.prop(\"عداد\", \"text\", \"العدد {{n}}\"))"))
        assertTrue(code.contains("AppLogic.color(\"عداد\", \"textColor\""))
        assertTrue(code.contains("AppLogic.color(\"عداد\", \"bgColor\""))
        // عنصر مخفي في البداية لكنه هدف: يُصدَّر بافتراضي false بدل أن يُحذف.
        assertTrue(code.contains("if (AppLogic.visible(\"سر\", false)) {"))
        // الزر ليس هدفًا: نصه ثابت.
        assertTrue(code.contains("text = \"اضغط\""))
    }

    @Test
    fun noLogicNoAppLogic() {
        val p = project(emptyList())
        val code = ComposeExporter.export(p)
        // نص فيه {{n}} يحتاج AppLogic.interp فقط.
        assertTrue(code.contains("AppLogic.interp(\"العدد {{n}}\")"))
        assertFalse(code.contains("Toast"))
        val plain = ComposeExporter.export(p.copy(screens = p.screens.map { s -> s.copy(elements = s.elements.drop(2)) }))
        assertFalse(plain.contains("AppLogic"))
    }

    @Test
    fun outputIsBalanced() {
        val code = ComposeExporter.export(project(steps, secondScreen = true))
        assertEquals(count(code, '{'), count(code, '}'))
        assertEquals(count(code, '('), count(code, ')'))
    }

    @Test
    fun missingNavigationTargetStillCompilesShape() {
        val code = ComposeExporter.export(project(listOf(Step(Steps.NAV, "غير موجودة"))))
        assertTrue(code.contains("Unit"))
        assertFalse(code.contains("onNavigate(\"screen"))
    }
}
