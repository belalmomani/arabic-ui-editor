package com.arabicuieditor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.data.model.ColorUtil
import com.arabicuieditor.data.model.DesignSystem
import com.arabicuieditor.data.model.DesignThemes
import com.arabicuieditor.data.model.Tokens

/** نظام التصميم الحالي للمشروع، لتقرأه حقول الخصائص فتعرض القيمة الفعلية للمراجع. */
val LocalDesignSystem = compositionLocalOf { DesignSystem.DEFAULT }

/**
 * لوحة نظام التصميم: سمات جاهزة، وألوان، وزوايا، وأحجام نصوص.
 * تغيير قيمة هنا يغيّر كل عنصر مرتبط بها (مرجع مثل «اللون الرئيسي») في كل الشاشات.
 */
@Composable
fun DesignSystemPanel(
    designSystem: DesignSystem,
    onThemeSelect: (String) -> Unit,
    onColor: (String, Int) -> Unit,
    onRadius: (String, Int) -> Unit,
    onTextSize: (String, Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.testTag("design_panel"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "note") {
            Text(
                stringResource(R.string.design_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item(key = "themes") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader(stringResource(R.string.design_themes))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DesignThemes.all.forEach { theme ->
                        FilterChip(
                            selected = theme.colors == designSystem.colors,
                            onClick = { onThemeSelect(theme.id) },
                            label = { Text(stringResource(theme.labelRes)) },
                            modifier = Modifier.testTag("theme_${theme.id}")
                        )
                    }
                    AssistChip(
                        onClick = onReset,
                        label = { Text(stringResource(R.string.design_reset)) },
                        modifier = Modifier.testTag("design_reset")
                    )
                }
            }
        }
        item(key = "colors") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(stringResource(R.string.design_colors))
                Tokens.colorNames.forEach { name ->
                    ColorField(
                        label = stringResource(Tokens.colorLabel(name)),
                        value = ColorUtil.format(designSystem.color(name)),
                        onValue = { hex -> ColorUtil.parse(hex)?.let { onColor(name, it) } },
                        modifier = Modifier.fillMaxWidth(),
                        tag = "ds_color_$name"
                    )
                }
            }
        }
        item(key = "radii") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader(stringResource(R.string.design_radii))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tokens.radiusNames.forEach { name ->
                        NumberField(
                            stringResource(Tokens.radiusLabel(name)), designSystem.radius(name), 0, 100,
                            { onRadius(name, it) }, Modifier.weight(1f), "ds_radius_$name"
                        )
                    }
                }
            }
        }
        item(key = "texts") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader(stringResource(R.string.design_texts))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tokens.textNames.forEach { name ->
                        NumberField(
                            stringResource(Tokens.textLabel(name)), designSystem.textSize(name), 8, 72,
                            { onTextSize(name, it) }, Modifier.weight(1f), "ds_text_$name"
                        )
                    }
                }
            }
        }
    }
}
