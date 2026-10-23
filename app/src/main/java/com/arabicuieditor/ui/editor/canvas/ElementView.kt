package com.arabicuieditor.ui.editor.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.FontWeights
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.TextAligns
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.boolProp
import com.arabicuieditor.data.model.colorProp
import com.arabicuieditor.data.model.intProp
import com.arabicuieditor.data.model.prop

/** العنصر المخفي يبقى ظاهرًا باهتًا في المحرر كي يمكن تحديده وإظهاره من جديد. */
private const val HIDDEN_ALPHA = 0.18f

private val GuideLine = Color(0xFFB0B7BF)
private val ContainerGuide = Color(0xFF5B9BD5)
private val ImageCross = Color(0xFFB0B7BF)

private fun argb(value: Int) = Color(value)

/**
 * يرسم عنصرًا واحدًا من خصائصه (كلها تُقرأ عبر [prop] والقيم الافتراضية في السجل).
 * [modifier] يحمل الموضع والحجم من الأب.
 * هذا رسم تحريري (للمحرر) وليس الكود المُصدَّر لاحقًا.
 *
 * ملاحظة: المحاذاة start/end تُرسم هنا وفق اتجاه مساحة التصميم (يسار-يمين).
 */
@Composable
fun ElementView(element: UiElement, modifier: Modifier) {
    val visible = element.boolProp(PropertyKeys.VISIBLE)
    val opacity = element.intProp(PropertyKeys.OPACITY) / 100f
    val base = modifier.alpha(if (visible) opacity else HIDDEN_ALPHA)

    val radius = element.intProp(PropertyKeys.CORNER_RADIUS).dp
    val shape: Shape = RoundedCornerShape(radius)
    val bg = argb(element.colorProp(PropertyKeys.BG_COLOR))
    val borderWidth = element.intProp(PropertyKeys.BORDER_WIDTH)
    val borderColor = argb(element.colorProp(PropertyKeys.BORDER_COLOR))

    // خلفية + حد + قص بنفس الشكل (للأنواع ذات الصندوق).
    fun Modifier.boxStyle(): Modifier = this
        .clip(shape)
        .background(bg, shape)
        .let { if (borderWidth > 0) it.border(borderWidth.dp, borderColor, shape) else it }

    val padded = Modifier.padding(
        start = element.intProp(PropertyKeys.PADDING_START).dp,
        top = element.intProp(PropertyKeys.PADDING_TOP).dp,
        end = element.intProp(PropertyKeys.PADDING_END).dp,
        bottom = element.intProp(PropertyKeys.PADDING_BOTTOM).dp
    )

    when (element.type) {
        ElementTypes.TEXT -> Box(base.boxStyle(), contentAlignment = alignmentOf(element)) {
            StyledText(element, element.prop(PropertyKeys.TEXT), padded)
        }

        ElementTypes.BUTTON -> Box(base.boxStyle(), contentAlignment = alignmentOf(element)) {
            StyledText(element, element.prop(PropertyKeys.TEXT), padded, singleLine = true)
        }

        ElementTypes.INPUT -> Box(base.boxStyle(), contentAlignment = alignmentOf(element)) {
            StyledText(element, element.prop(PropertyKeys.HINT), padded, singleLine = true)
        }

        ElementTypes.IMAGE -> Box(base.boxStyle()) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 1.dp.toPx()
                drawLine(ImageCross, Offset.Zero, Offset(size.width, size.height), stroke)
                drawLine(ImageCross, Offset(size.width, 0f), Offset(0f, size.height), stroke)
            }
        }

        ElementTypes.CONTAINER -> Box(
            base.boxStyle().let { if (borderWidth == 0) it.dashedGuide(ContainerGuide) else it }
        )

        ElementTypes.CARD -> {
            // الظل جزء من مظهر البطاقة؛ يتبع زواياها.
            Box(base.shadow(3.dp, shape).boxStyle())
        }

        ElementTypes.DIVIDER -> Box(base, contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(bg)
            )
        }

        ElementTypes.SPACER -> Box(base.dashedGuide(GuideLine))

        ElementTypes.LIST -> ListView(element, base.boxStyle(), padded)

        else -> Box(base.background(Color(0xFFFFE0E0)))
    }
}

@Composable
private fun StyledText(
    element: UiElement,
    text: String,
    modifier: Modifier,
    singleLine: Boolean = false
) {
    Text(
        text = text,
        color = argb(element.colorProp(PropertyKeys.TEXT_COLOR)),
        fontSize = element.intProp(PropertyKeys.FONT_SIZE).sp,
        fontWeight = fontWeightOf(element.prop(PropertyKeys.FONT_WEIGHT)),
        textAlign = textAlignOf(element.prop(PropertyKeys.TEXT_ALIGN)),
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun ListView(element: UiElement, container: Modifier, padded: Modifier) {
    val items = element.prop(PropertyKeys.ITEMS).split('\n').filter { it.isNotEmpty() }
    val itemHeight = element.intProp(PropertyKeys.ITEM_HEIGHT).dp
    val divider = GuideLine
    Column(container.then(padded)) {
        items.forEachIndexed { index, item ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .drawBehind {
                        if (index < items.lastIndex) {
                            val y = size.height - 0.5.dp.toPx()
                            drawLine(divider, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                StyledText(element, item, Modifier, singleLine = true)
            }
        }
    }
}

private fun alignmentOf(element: UiElement): Alignment = when (element.prop(PropertyKeys.TEXT_ALIGN)) {
    TextAligns.CENTER -> Alignment.Center
    TextAligns.END -> Alignment.CenterEnd
    else -> Alignment.CenterStart
}

private fun textAlignOf(value: String): TextAlign = when (value) {
    TextAligns.CENTER -> TextAlign.Center
    TextAligns.END -> TextAlign.End
    else -> TextAlign.Start
}

private fun fontWeightOf(value: String): FontWeight = when (value) {
    FontWeights.BOLD -> FontWeight.Bold
    FontWeights.MEDIUM -> FontWeight.Medium
    else -> FontWeight.Normal
}

/** إطار منقّط يدل على موضع عنصر شفاف (حاوية/مساحة فارغة) أثناء التصميم فقط. */
private fun Modifier.dashedGuide(color: Color): Modifier = drawBehind {
    drawRect(
        color = color,
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )
    )
}
