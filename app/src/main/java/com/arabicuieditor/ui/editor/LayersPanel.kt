package com.arabicuieditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
    modifier: Modifier = Modifier
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
    LazyColumn(modifier = modifier) {
        items(rows, key = { it.element.id }) { row ->
            val element = row.element
            val selected = element.id == selectedId
            val typeLabel = stringResource(elementLabelRes(element.type))
            val name = element.properties[PropertyKeys.NAME]?.takeIf { it.isNotBlank() } ?: typeLabel
            val hiddenMark = if (element.boolProp(PropertyKeys.VISIBLE)) "" else " · " + stringResource(R.string.layer_hidden)
            val layoutMark = if (ElementTree.canContain(element.type)) {
                " · " + stringResource(layoutLabelRes(element.childLayout))
            } else ""
            Text(
                text = "$name · $typeLabel$layoutMark$hiddenMark",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onSelect(element.id) }
                    .padding(start = (12 + row.depth * 16).dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
            )
        }
    }
}

/** نص نوع التخطيط (حر/عمود/صف/صندوق). */
fun layoutLabelRes(layout: ChildLayout): Int = when (layout) {
    ChildLayout.COLUMN -> R.string.layout_column
    ChildLayout.ROW -> R.string.layout_row
    ChildLayout.BOX -> R.string.layout_box
    else -> R.string.layout_free
}
