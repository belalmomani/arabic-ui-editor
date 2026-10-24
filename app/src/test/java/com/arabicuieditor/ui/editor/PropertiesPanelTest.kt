package com.arabicuieditor.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.newElement
import com.arabicuieditor.ui.editor.canvas.DesignRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * اختبار لوحة الخصائص بتفاعل حقيقي (Robolectric): كل أداة تنادي onSetProperty بالمفتاح والقيمة الصحيحين،
 * واللوحة تعرض خصائص النوع المحدد فقط.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class PropertiesPanelTest {

    @get:Rule
    val rule = createComposeRule()

    private val sets = mutableListOf<Pair<String, String>>()
    private val rects = mutableListOf<DesignRect>()
    private val reorders = mutableListOf<ReorderOp>()
    private var duplicated = 0
    private var deleted = 0

    private fun button() = newElement(ElementTypes.BUTTON, 20f, 30f, "زر")

    private fun show(element: UiElement?) {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 560.dp)) {
                    PropertiesPanel(
                        element = element,
                        rect = element?.let { DesignRect(20f, 30f, 120f, 44f) },
                        onSetProperty = { k, v -> sets += k to v },
                        onSetRect = { rects += it },
                        onDuplicate = { duplicated++ },
                        onDelete = { deleted++ },
                        onReorder = { reorders += it },
                        modifier = Modifier.size(360.dp, 560.dp)
                    )
                }
            }
        }
    }

    private fun scrollTo(tag: String) {
        rule.onNodeWithTag("props_list").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun noSelectionShowsNoEditors() {
        show(null)
        rule.onNodeWithTag("props_list").assertDoesNotExist()
    }

    @Test
    fun incrementingFontSizeReportsNewValue() {
        show(button())
        scrollTo("prop_fontSize_inc")
        rule.onNodeWithTag("prop_fontSize_inc").performClick()
        assertEquals("fontSize" to "15", sets.last())
    }

    @Test
    fun typingHexColorReportsNormalizedArgb() {
        show(button())
        scrollTo("prop_bgColor_hex")
        rule.onNodeWithTag("prop_bgColor_hex").performTextReplacement("#FF0000")
        assertTrue("sets = $sets", sets.contains("bgColor" to "#FFFF0000"))
    }

    @Test
    fun invalidHexIsNotReported() {
        show(button())
        scrollTo("prop_bgColor_hex")
        rule.onNodeWithTag("prop_bgColor_hex").performTextReplacement("#12")
        assertTrue("sets = $sets", sets.none { it.first == "bgColor" })
    }

    @Test
    fun choosingSwatchReportsColor() {
        show(button())
        scrollTo("prop_bgColor_#FFB3261E")
        rule.onNodeWithTag("prop_bgColor_#FFB3261E").performClick()
        assertEquals("bgColor" to "#FFB3261E", sets.last())
    }

    @Test
    fun choosingFontWeightReportsChoice() {
        show(button())
        scrollTo("prop_fontWeight_bold")
        rule.onNodeWithTag("prop_fontWeight_bold").performClick()
        assertEquals("fontWeight" to "bold", sets.last())
    }

    @Test
    fun togglingVisibilityReportsFalse() {
        show(button())
        scrollTo("prop_visible")
        rule.onNodeWithTag("prop_visible").performClick()
        assertEquals("visible" to "false", sets.last())
    }

    @Test
    fun geometryEditsReportFullRect() {
        show(button())
        scrollTo("geo_w_inc")
        rule.onNodeWithTag("geo_w_inc").performClick()
        val r = rects.last()
        assertEquals(121f, r.w, 0f)
        assertEquals(20f, r.x, 0f)
        assertEquals(44f, r.h, 0f)
    }

    @Test
    fun actionsAreForwarded() {
        show(button())
        rule.onNodeWithTag("act_duplicate").performClick()
        rule.onNodeWithTag("act_delete").performClick()
        rule.onNodeWithTag("act_front").performClick()
        rule.onNodeWithTag("act_back").performClick()
        assertEquals(1, duplicated)
        assertEquals(1, deleted)
        assertEquals(listOf(ReorderOp.TO_FRONT, ReorderOp.TO_BACK), reorders)
    }

    @Test
    fun imageShowsNoTextProperties() {
        show(newElement(ElementTypes.IMAGE, 0f, 0f, "x"))
        rule.onAllNodesWithTag("prop_text").assertCountEquals(0)
        rule.onAllNodesWithTag("prop_fontSize").assertCountEquals(0)
        scrollTo("prop_cornerRadius")
        rule.onAllNodesWithTag("prop_cornerRadius").assertCountEquals(1)
    }
}
