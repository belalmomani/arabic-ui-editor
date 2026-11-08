package com.arabicuieditor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.ui.editor.canvas.ScreenRect
import com.arabicuieditor.ui.theme.AppIcons
import com.arabicuieditor.ui.theme.Palette
import kotlin.math.roundToInt

private val BAR_BUTTON = 40.dp
private const val BAR_BUTTONS = 5
private val BAR_PAD = 4.dp
private val BAR_ABOVE_GAP = 20.dp
private val BAR_BELOW_GAP = 66.dp
private val BAR_EDGE = 4.dp

/**
 * شريط أدوات عائم فوق العنصر المحدد (أو تحته إن لم يوجد مكان): تكرار، حذف، للأمام، للخلف، فتح الخصائص.
 * [rect] إطار العنصر بإحداثيات المساحة؛ [canvasW]/[canvasH] حجم المساحة بالبكسل.
 */
@Composable
internal fun ElementFloatingBar(
    rect: ScreenRect,
    canvasW: Float,
    canvasH: Float,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val w = (BAR_BUTTON.value * BAR_BUTTONS + BAR_PAD.value * 2) * density
    val h = (BAR_BUTTON.value + BAR_PAD.value * 2) * density
    val edge = BAR_EDGE.value * density
    val cx = (rect.left + rect.right) / 2f
    val x = (cx - w / 2f).coerceIn(edge, (canvasW - w - edge).coerceAtLeast(edge))
    val above = rect.top - h - BAR_ABOVE_GAP.value * density
    val yRaw = if (above >= edge) above else rect.bottom + BAR_BELOW_GAP.value * density
    val y = yRaw.coerceIn(edge, (canvasH - h - edge).coerceAtLeast(edge))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, Palette.Border),
        modifier = modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .testTag("element_floating_bar")
    ) {
        Row(Modifier.padding(BAR_PAD), verticalAlignment = Alignment.CenterVertically) {
            BarButton(AppIcons.Duplicate, stringResource(R.string.fq_duplicate), "bar_duplicate", Palette.TextPrimary, onDuplicate)
            BarButton(AppIcons.ChevronUp, stringResource(R.string.bar_forward), "bar_forward", Palette.TextPrimary, onForward)
            BarButton(AppIcons.ChevronDown, stringResource(R.string.bar_backward), "bar_backward", Palette.TextPrimary, onBackward)
            BarButton(AppIcons.Edit, stringResource(R.string.bar_edit), "bar_edit", Palette.Primary, onEdit)
            BarButton(AppIcons.Trash, stringResource(R.string.action_delete), "bar_delete", Palette.Error, onDelete)
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, description: String, tag: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(BAR_BUTTON).testTag(tag)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}
