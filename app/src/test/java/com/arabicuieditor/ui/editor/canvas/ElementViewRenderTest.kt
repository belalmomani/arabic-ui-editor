package com.arabicuieditor.ui.editor.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.newElement
import com.arabicuieditor.data.model.withProp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * دليل أن الخصائص تنعكس فعلًا على ما يُرسم في المعاينة: يرسم ElementView على خلفية بيضاء
 * ويقرأ بكسلات الصورة الناتجة. الكثافة 1، فكل dp = بكسل.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class ElementViewRenderTest {

    @get:Rule
    val rule = createComposeRule()

    private fun redButton(): UiElement =
        newElement(ElementTypes.BUTTON, 0f, 0f, "")
            .withProp(PropertyKeys.BG_COLOR, "#FFFF0000")
            .withProp(PropertyKeys.CORNER_RADIUS, "0")

    /** يرسم العنصر بحجم 100×40 في أعلى-يسار خلفية بيضاء 200×100 ويعيد لون البكسل (x, y). */
    private fun pixel(element: UiElement, x: Int, y: Int): Color {
        rule.setContent {
            Box(Modifier.size(200.dp, 100.dp).background(Color.White).testTag("host")) {
                ElementView(element, Modifier.size(100.dp, 40.dp))
            }
        }
        rule.waitForIdle()
        return rule.onNodeWithTag("host").captureToImage().toPixelMap()[x, y]
    }

    @Test
    fun backgroundColorShowsInPreview() {
        val c = pixel(redButton(), 50, 3)
        assertTrue("pixel = $c", c.red > 0.9f && c.green < 0.15f && c.blue < 0.15f)
    }

    @Test
    fun opacityBlendsWithBackground() {
        val c = pixel(redButton().withProp(PropertyKeys.OPACITY, "50"), 50, 3)
        // أحمر نصف شفاف فوق الأبيض: المركّب الأخضر قرابة 0.5
        assertTrue("pixel = $c", c.green in 0.35f..0.65f && c.red > 0.9f)
    }

    @Test
    fun hiddenElementIsGhosted() {
        val c = pixel(redButton().withProp(PropertyKeys.VISIBLE, "false"), 50, 3)
        // باهت جدًا لكنه ما زال ظاهرًا ليمكن تحديده: أخضر قريب من الأبيض وليس أبيض تمامًا
        assertTrue("pixel = $c", c.green > 0.7f && c.green < 0.95f)
    }

    @Test
    fun borderShowsInPreview() {
        val el = redButton()
            .withProp(PropertyKeys.BORDER_WIDTH, "6")
            .withProp(PropertyKeys.BORDER_COLOR, "#FF0000FF")
        val c = pixel(el, 50, 2)
        assertTrue("pixel = $c", c.blue > 0.9f && c.red < 0.15f)
    }

    @Test
    fun cornerRadiusClipsTheCorner() {
        val square = pixel(redButton(), 1, 1)
        assertTrue("square corner = $square", square.red > 0.9f && square.green < 0.15f)
    }

    @Test
    fun roundedCornerLeavesBackgroundVisible() {
        val round = pixel(redButton().withProp(PropertyKeys.CORNER_RADIUS, "20"), 1, 1)
        assertTrue("round corner = $round", round.green > 0.9f && round.blue > 0.9f)
    }
}
