package com.arabicuieditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.ui.platform.testTag
import com.arabicuieditor.ui.editor.canvas.AlignEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.boolProp

private data class LayerRow(val element: UiElement, val depth: Int)

private fun layerRows(list: List<UiElement>, depth: Int, out: MutableList<LayerRow>) {
    for (e in list.asReversed()) {
        out += LayerRow(e, depth)
        layerRows(e.children, depth + 1, out)
    }
}

/**
 * قائمة الطبقات كشجرة: الأعلى في القائمة هو الأعلى في الشاشة، وأبناء الحاوية تحتها بإزاحة.
 * اختيار عنصر منها هو الطريقة المضمونة لتحديد عنصر مغطّى أو صغير أو داخل حاوية.
 */
@Composable
fun LayersPanel(
    elements: List<UiElement>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    multiMode: Boolean = false,
    multi: Set<String> = emptySet(),
    onMultiMode: (Boolean) -> Unit = {},
    onToggle: (String) -> Unit = {},
    onAlignMulti: (AlignEdge) -> Unit = {},
    onDistributeMulti: (Boolean) -> Unit = {},
    onDeleteMulti: () -> Unit = {}
) {
    if (elements.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.layers_empty),
                style = MaterialTheme.typography.bodySmall
            )
        }
        return
    }
    val rows = ArrayList<LayerRow>().also { layerRows(elements, 0, it) }
    Column(modifier) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = multiMode,
            onClick = { onMultiMode(!multiMode) },
            label = { Text(stringResource(R.string.multi_mode)) },
            modifier = Modifier.testTag("multi_toggle")
        )
        if (multiMode) {
            Text(stringResource(R.string.multi_count, multi.size), style = MaterialTheme.typography.labelMedium)
            if (multi.isNotEmpty()) {
                AssistChip(onClick = onDeleteMulti, label = { Text(stringResource(R.string.multi_delete)) }, modifier = Modifier.testTag("multi_delete"))
            }
            if (multi.size >= 2) {
                AssistChip(onClick = { onAlignMulti(AlignEdge.LEFT) }, label = { Text(stringResource(R.string.align_to_left)) }, modifier = Modifier.testTag("multi_align_left"))
                AssistChip(onClick = { onAlignMulti(AlignEdge.CENTER_H) }, label = { Text(stringResource(R.string.align_to_center_h)) }, modifier = Modifier.testTag("multi_align_center_h"))
                AssistChip(onClick = { onAlignMulti(AlignEdge.RIGHT) }, label = { Text(stringResource(R.string.align_to_right)) }, modifier = Modifier.testTag("multi_align_right"))
                AssistChip(onClick = { onAlignMulti(AlignEdge.TOP) }, label = { Text(stringResource(R.string.align_to_top)) }, modifier = Modifier.testTag("multi_align_top"))
                AssistChip(onClick = { onAlignMulti(AlignEdge.CENTER_V) }, label = { Text(stringResource(R.string.align_to_center_v)) }, modifier = Modifier.testTag("multi_align_center_v"))
                AssistChip(onClick = { onAlignMulti(AlignEdge.BOTTOM) }, label = { Text(stringResource(R.string.align_to_bottom)) }, modifier = Modifier.testTag("multi_align_bottom"))
            }
            if (multi.size >= 3) {
                AssistChip(onClick = { onDistributeMulti(true) }, label = { Text(stringResource(R.string.distribute_h)) }, modifier = Modifier.testTag("multi_dist_h"))
                AssistChip(onClick = { onDistributeMulti(false) }, label = { Text(stringResource(R.string.distribute_v)) }, modifier = Modifier.testTag("multi_dist_v"))
            }
        }
    }
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(rows, key = { it.element.id }) { row ->
            val element = row.element
            val checked = element.id in multi
            val selected = if (multiMode) checked else element.id == selectedId
            val typeLabel = stringResource(elementLabelRes(element.type))
            val name = element.properties[PropertyKeys.NAME]?.takeIf { it.isNotBlank() } ?: typeLabel
            val hiddenMark = if (element.boolProp(PropertyKeys.VISIBLE)) "" else " · " + stringResource(R.string.layer_hidden)
            val layoutMark = if (ElementTree.canContain(element.type)) {
                " · " + stringResource(layoutLabelRes(element.childLayout))
            } else ""
            Text(
                text = (if (multiMode) (if (checked) "☑ " else "☐ ") else "") + "$name · $typeLabel$layoutMark$hiddenMark",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { if (multiMode) onToggle(element.id) else onSelect(element.id) }
                    .testTag("layer_${element.id}")
                    .padding(start = (12 + row.depth * 16).dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
            )
        }
    }
    }
}

/** نص نوع التخطيط (حر/عمود/صف/صندوق). */
fun layoutLabelRes(layout: ChildLayout): Int = when (layout) {
    ChildLayout.COLUMN -> R.string.layout_column
    ChildLayout.ROW -> R.string.layout_row
    ChildLayout.BOX -> R.string.layout_box
    ChildLayout.CONSTRAINT -> R.string.layout_constraint
    else -> R.string.layout_free
}
