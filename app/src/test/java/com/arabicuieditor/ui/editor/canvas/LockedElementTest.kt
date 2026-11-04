package com.arabicuieditor.ui.editor.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h470dp-mdpi")
class LockedElementTest {

    @get:Rule
    val rule = createComposeRule()

    private val locked = UiElement(
        id = "a", type = ElementTypes.BUTTON,
        layout = ElementLayout(50f, 50f, SizeSpec.fixed(100f), SizeSpec.fixed(44f)),
        properties = mapOf(PropertyKeys.LOCKED to "true")
    )
    private var rectChanges by mutableStateOf(0)
    private var selected by mutableStateOf<String?>(null)

    @Test
    fun lockedElementIsSelectableButDoesNotMove() {
        rule.setContent {
            Box(Modifier.size(320.dp, 470.dp).testTag("host")) {
                DesignCanvas(
                    screen = UiScreen("s", "ش", 300f, 400f, listOf(locked)),
                    selectedId = selected,
                    viewport = Viewport(1f, 0f, 0f),
                    onViewportChange = {},
                    onSelect = { selected = it },
                    onElementRectChange = { _, _ -> rectChanges++ },
                    onSizeChange = {},
                    modifier = Modifier.size(320.dp, 470.dp)
                )
            }
        }
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(100f, 72f))
            moveBy(Offset(40f, 30f))
            up()
        }
        rule.waitForIdle()
        assertEquals(0, rectChanges)
        assertEquals("a", selected)
    }
}
