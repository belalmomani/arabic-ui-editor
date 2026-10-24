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
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import com.arabicuieditor.data.model.ElementLayout
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * اختبار تفاعل حقيقي لمساحة التصميم: يحقن لمسات فعلية في DesignCanvas (بدل الاكتفاء بنجاح البناء).
 * يعمل على JVM عبر Robolectric بكثافة 1 (كل dp = بكسل واحد)، فالأرقام مباشرة.
 *
 * الإطار 300x400dp، والمساحة 320x470dp، والتكبير 1 والإزاحة 0، فإحداثيات اللمس = إحداثيات التصميم.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h470dp-mdpi")
class DesignCanvasGestureTest {

    @get:Rule
    val rule = createComposeRule()

    private val buttonId = "btn-1"

    private fun button() = UiElement(
        id = buttonId,
        type = ElementTypes.BUTTON,
        layout = ElementLayout(50f, 50f, SizeSpec.fixed(100f), SizeSpec.fixed(44f)),
        properties = mapOf("text" to "زر")
    )

    private var screen by mutableStateOf(UiScreen("s", "شاشة", 300f, 400f, listOf(button())))
    private var selected by mutableStateOf<String?>(null)
    private var viewport by mutableStateOf(Viewport(1f, 0f, 0f))

    private fun rectOfButton(): DesignRect =
        CanvasMath.resolveRect(screen.elements.first { it.id == buttonId }, screen.width, screen.height)

    private fun setContent() {
        rule.setContent {
            Box(Modifier.size(320.dp, 470.dp).testTag("host")) {
                DesignCanvas(
                    screen = screen,
                    selectedId = selected,
                    viewport = viewport,
                    onViewportChange = { viewport = it },
                    onSelect = { selected = it },
                    onElementRectChange = { id, r ->
                        screen = screen.copy(elements = screen.elements.map { e ->
                            if (e.id != id) e else e.copy(
                                layout = e.layout.copy(
                                    x = r.x, y = r.y,
                                    width = SizeSpec.fixed(r.w), height = SizeSpec.fixed(r.h)
                                )
                            )
                        })
                    },
                    onSizeChange = {},
                    modifier = Modifier.size(320.dp, 470.dp)
                )
            }
        }
    }

    @Test
    fun tapOnElementSelectsIt() {
        setContent()
        rule.onNodeWithTag("host").performTouchInput { click(Offset(100f, 72f)) }
        rule.waitForIdle()
        assertEquals(buttonId, selected)
    }

    @Test
    fun tapOnEmptyAreaClearsSelection() {
        selected = buttonId
        setContent()
        rule.onNodeWithTag("host").performTouchInput { click(Offset(250f, 350f)) }
        rule.waitForIdle()
        assertNull(selected)
    }

    @Test
    fun oneFingerDragMovesElement() {
        setContent()
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(100f, 72f))
            moveBy(Offset(40f, 30f))
            up()
        }
        rule.waitForIdle()
        assertEquals(buttonId, selected)
        val r = rectOfButton()
        assertEquals(90f, r.x, 1f)
        assertEquals(80f, r.y, 1f)
        assertEquals(100f, r.w, 0f)
        assertEquals(44f, r.h, 0f)
    }

    @Test
    fun draggingCornerHandleResizesElement() {
        selected = buttonId
        setContent()
        // المقبض السفلي الأيمن عند (150, 94)
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(150f, 94f))
            moveBy(Offset(30f, 20f))
            up()
        }
        rule.waitForIdle()
        val r = rectOfButton()
        assertEquals(50f, r.x, 0f)
        assertEquals(50f, r.y, 0f)
        assertEquals(130f, r.w, 1f)
        assertEquals(64f, r.h, 1f)
    }

    @Test
    fun draggingEmptyAreaPansWorkspace() {
        setContent()
        rule.onNodeWithTag("host").performTouchInput {
            down(Offset(250f, 350f))
            moveBy(Offset(25f, -10f))
            up()
        }
        rule.waitForIdle()
        assertEquals(25f, viewport.offsetX, 1f)
        assertEquals(-10f, viewport.offsetY, 1f)
        // العنصر لم يتحرك
        assertEquals(50f, rectOfButton().x, 0f)
    }

    @Test
    fun twoFingerPinchZoomsIn() {
        setContent()
        rule.onNodeWithTag("host").performTouchInput {
            pinch(
                start0 = Offset(140f, 235f), start1 = Offset(180f, 235f),
                end0 = Offset(100f, 235f), end1 = Offset(220f, 235f)
            )
        }
        rule.waitForIdle()
        assertTrue("scale was ${viewport.scale}", viewport.scale > 1.5f)
    }
}
