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
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.boolProp

/**
 * قائمة الطبقات: الأعلى في القائمة هو الأعلى في الشاشة. اختيار عنصر منها هو الطريقة
 * المضمونة لتحديد عنصر مغطّى بعناصر أخرى متداخلة.
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
    LazyColumn(modifier = modifier) {
        items(elements.asReversed(), key = { it.id }) { element ->
            val selected = element.id == selectedId
            val typeLabel = stringResource(elementLabelRes(element.type))
            val name = element.properties[PropertyKeys.NAME]?.takeIf { it.isNotBlank() } ?: typeLabel
            val hiddenMark = if (element.boolProp(PropertyKeys.VISIBLE)) "" else " · " + stringResource(R.string.layer_hidden)
            Text(
                text = "$name · $typeLabel$hiddenMark",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onSelect(element.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}
