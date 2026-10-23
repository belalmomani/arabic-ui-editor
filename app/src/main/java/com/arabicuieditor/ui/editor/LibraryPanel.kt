package com.arabicuieditor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.data.model.ComponentLibrary
import com.arabicuieditor.data.model.ComponentPreset
import com.arabicuieditor.data.model.PresetCategory

/** مكتبة المكونات الجاهزة: فئات في الأعلى، ومكوّنات الفئة المختارة في صف أفقي. نقرة تضيف المكوّن. */
@Composable
fun LibraryPanel(
    onAdd: (ComponentPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var categoryIndex by rememberSaveable { mutableIntStateOf(0) }
    val categories = PresetCategory.entries
    val category = categories[categoryIndex.coerceIn(0, categories.lastIndex)]

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEachIndexed { index, c ->
                FilterChip(
                    selected = c == category,
                    onClick = { categoryIndex = index },
                    label = { Text(stringResource(c.labelRes)) },
                    modifier = Modifier.testTag("cat_${c.name}")
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ComponentLibrary.byCategory(category).forEach { preset ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .width(112.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAdd(preset) }
                        .testTag("preset_${preset.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(preset.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}
