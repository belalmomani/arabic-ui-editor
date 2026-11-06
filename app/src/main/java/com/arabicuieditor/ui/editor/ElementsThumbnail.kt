package com.arabicuieditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.arabicuieditor.data.model.DesignSystem
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.Tokens
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.boolProp
import com.arabicuieditor.data.model.intProp
import com.arabicuieditor.data.model.resolved
import com.arabicuieditor.ui.editor.canvas.DesignRect
import com.arabicuieditor.ui.editor.canvas.ElementView
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import com.arabicuieditor.ui.editor.canvas.ThumbnailMath

/**
 * مصغّرة مرسومة فعلًا بنفس رسم المحرر (لا صورة ثابتة): تتبع نظام التصميم والوضع الليلي.
 * [wholeScreen] true يعرض الشاشة كلها (قوالب)، وإلا يُقتص حول المحتوى فقط (مكوّنات).
 * لا تستقبل لمسًا؛ النقر يتولاه الحاوي.
 */
@Composable
fun ScreenThumbnail(
    screen: UiScreen,
    designSystem: DesignSystem,
    targetWidthDp: Float,
    targetHeightDp: Float,
    wholeScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val layout = remember(screen) { LayoutEngine.layout(screen) }
    val elements = remember(screen, designSystem) {
        ElementTree.flatten(screen.elements).map { it.resolved(designSystem) }.associateBy { it.id }
    }
    val crop: DesignRect = remember(layout, wholeScreen) {
        if (wholeScreen) DesignRect(0f, 0f, screen.width, screen.height)
        else ThumbnailMath.bounds(layout, layout.paintOrder) ?: DesignRect(0f, 0f, screen.width, screen.height)
    }
    val scale = ThumbnailMath.scaleFor(crop, targetWidthDp, targetHeightDp)
    val density = LocalDensity.current.density

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .size((crop.w * scale).dp, (crop.h * scale).dp)
                .clipToBounds()
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(screen.width.dp, screen.height.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = -crop.x * scale * density
                        translationY = -crop.y * scale * density
                    }
                    .background(if (wholeScreen) Color(designSystem.color(Tokens.BACKGROUND)) else Color.Transparent)
            ) {
                layout.paintOrder.forEach { id ->
                    val element = elements[id]
                    val rect = layout.frames[id]
                    if (element != null && rect != null && element.boolProp(PropertyKeys.VISIBLE)) {
                        key(id) {
                            ElementView(
                                element = element,
                                modifier = Modifier
                                    .absoluteOffset(rect.x.dp, rect.y.dp)
                                    .size(rect.w.dp, rect.h.dp)
                                    .graphicsLayer {
                                        rotationZ = element.intProp(PropertyKeys.ROTATION).toFloat()
                                        // القلب حول المركز: لا يغيّر حدود العنصر فلا يتأثر اللمس ولا التحديد.
                                        if (element.boolProp(PropertyKeys.FLIP_X)) scaleX = -1f
                                        if (element.boolProp(PropertyKeys.FLIP_Y)) scaleY = -1f
                                    }
                                    .clipToBounds()
                            )
                        }
                    }
                }
            }
        }
    }
}
