package com.arabicuieditor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import com.arabicuieditor.data.model.ElementTypes

/**
 * قائمة العناصر: ثمانية عناصر في صفين ثابتين (بلا تمرير كي لا يتعارض مع السحب).
 * - نقرة: إضافة العنصر إلى مساحة التصميم.
 * - سحب: يتبع الإصبع عنصر شبحي، وعند الإفلات فوق المساحة يُضاف هناك.
 * مواضع السحب تُرسل بإحداثيات النافذة (window).
 */
@Composable
fun ElementPalette(
    onTapAdd: (String) -> Unit,
    onDragStart: (type: String, windowPos: Offset) -> Unit,
    onDragMove: (windowPos: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElementTypes.all.chunked(4).forEach { rowTypes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTypes.forEach { type ->
                    PaletteItem(
                        type = type,
                        onTapAdd = onTapAdd,
                        onDragStart = onDragStart,
                        onDragMove = onDragMove,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteItem(
    type: String,
    onTapAdd: (String) -> Unit,
    onDragStart: (String, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier
) {
    var windowPos by remember { mutableStateOf(Offset.Zero) }
    Surface(
        modifier = modifier
            .height(40.dp)
            .onGloballyPositioned { windowPos = it.positionInWindow() }
            .pointerInput(type) {
                detectTapGestures(onTap = { onTapAdd(type) })
            }
            .pointerInput(type) {
                detectDragGestures(
                    onDragStart = { offset -> onDragStart(type, windowPos + offset) },
                    onDrag = { change, _ ->
                        change.consume()
                        onDragMove(windowPos + change.position)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel
                )
            },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(elementLabelRes(type)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1
            )
        }
    }
}
