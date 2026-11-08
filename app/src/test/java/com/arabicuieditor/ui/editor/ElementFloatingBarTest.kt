package com.arabicuieditor.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.arabicuieditor.ui.editor.canvas.ScreenRect
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h640dp-mdpi")
class ElementFloatingBarTest {
    @get:Rule
    val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(rect: ScreenRect) {
        rule.setContent {
            Box(Modifier.size(400.dp, 600.dp)) {
                ElementFloatingBar(
                    rect = rect, canvasW = 400f, canvasH = 600f,
                    onDuplicate = { calls += "dup" }, onDelete = { calls += "del" },
                    onForward = { calls += "fwd" }, onBackward = { calls += "back" }, onEdit = { calls += "edit" }
                )
            }
        }
    }

    @Test
    fun buttonsReportTheirActions() {
        show(ScreenRect(100f, 200f, 200f, 260f))
        rule.onNodeWithTag("element_floating_bar").assertIsDisplayed()
        listOf("bar_duplicate", "bar_forward", "bar_backward", "bar_edit", "bar_delete").forEach {
            rule.onNodeWithTag(it).performClick()
        }
        assertEquals(listOf("dup", "fwd", "back", "edit", "del"), calls)
    }

    @Test
    fun barStaysVisibleWhenTheElementIsAtTheTopEdge() {
        show(ScreenRect(150f, 0f, 250f, 40f))
        rule.onNodeWithTag("element_floating_bar").assertIsDisplayed()
    }
}
