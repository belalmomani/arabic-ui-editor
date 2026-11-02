package com.arabicuieditor.ui.editor.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h470dp-mdpi")
class MultiSelectCanvasTest {

    @get:Rule
    val rule = createComposeRule()

    private fun button(id: String, x: Float, y: Float) = UiElement(
        id = id, type = ElementTypes.BUTTON,
        layout = ElementLayout(x, y, SizeSpec.fixed(100f), SizeSpec.fixed(44f)),
        properties = mapOf("text" to "زر")
    )

    private val screen = UiScreen("s", "شاشة", 300f, 400f, listOf(button("a", 20f, 20f), button("b", 20f, 120f)))
    private var toggles by mutableStateOf(listOf<String>())
    private var selected by mutableStateOf<String?>(null)

    private fun setContent(multi: Boolean) {
        rule.setContent {
            Box(Modifier.size(320.dp, 470.dp).testTag("host")) {
                DesignCanvas(
                    screen = screen,
                    selectedId = null,
                    viewport = Viewport(1f, 0f, 0f),
                    onViewportChange = {},
                    onSelect = { selected = it },
                    onElementRectChange = { _, _ -> },
                    onSizeChange = {},
                    modifier = Modifier.size(320.dp, 470.dp),
                    multiMode = multi,
                    multiIds = toggles.toSet(),
                    onToggleMulti = { toggles = toggles + it }
                )
            }
        }
    }

    @Test
    fun tapsToggleInMultiMode() {
        setContent(true)
        rule.onNodeWithTag("host").performTouchInput { click(Offset(60f, 40f)) }
        rule.onNodeWithTag("host").performTouchInput { click(Offset(60f, 140f)) }
        rule.waitForIdle()
        assertEquals(listOf("a", "b"), toggles)
        assertNull(selected)
    }

    @Test
    fun emptyTapDoesNothingInMultiMode() {
        setContent(true)
        rule.onNodeWithTag("host").performTouchInput { click(Offset(250f, 350f)) }
        rule.waitForIdle()
        assertEquals(emptyList<String>(), toggles)
    }
}
