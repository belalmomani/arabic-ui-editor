package com.arabicuieditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyContext
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.SCREEN_TYPE
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.prop
import com.arabicuieditor.ui.projects.NameDialog

private enum class ScreenDialog { RENAME, DELETE }

private fun dims(w: Float, h: Float) = "${w.toInt()}×${h.toInt()}"

/**
 * لوحة الشاشة: إدارة الشاشات (تسمية، نسخ، حذف، شاشة البداية، التنقل)، وحجم المعاينة،
 * وحجم التصميم الأساسي، وتخطيط جذر الشاشة.
 *
 * [previewWidth]/[previewHeight] حجم المعاينة الفعلي الحالي (يساوي حجم التصميم إن لم يُغيَّر).
 */
@Composable
fun ScreenPanel(
    project: Project,
    screen: UiScreen,
    previewWidth: Float,
    previewHeight: Float,
    onSelectScreen: (String) -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSetStart: () -> Unit,
    onSetPreview: (Float, Float) -> Unit,
    onSetScreenSize: (Float, Float) -> Unit,
    onSetLayout: (ChildLayout) -> Unit,
    onSetScreenProperty: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var dialog by remember { mutableStateOf<ScreenDialog?>(null) }
    val isStart = project.effectiveStartScreenId == screen.id
    val atBase = previewWidth == screen.width && previewHeight == screen.height
    val screenDefs = PropertyRegistry.forContext(
        SCREEN_TYPE, PropertyContext(ownLayout = screen.childLayout, isScreen = true)
    )

    LazyColumn(modifier = modifier.testTag("screen_panel"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = screen.name + if (isStart) "  ★ " + stringResource(R.string.screen_is_start) else "",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { dialog = ScreenDialog.RENAME },
                        label = { Text(stringResource(R.string.action_rename)) },
                        modifier = Modifier.testTag("act_screen_rename")
                    )
                    AssistChip(
                        onClick = onDuplicate,
                        label = { Text(stringResource(R.string.action_duplicate_screen)) },
                        modifier = Modifier.testTag("act_screen_duplicate")
                    )
                    AssistChip(
                        onClick = onSetStart,
                        enabled = !isStart,
                        label = { Text(stringResource(R.string.action_set_start_screen)) },
                        modifier = Modifier.testTag("act_screen_start")
                    )
                    AssistChip(
                        onClick = { dialog = ScreenDialog.DELETE },
                        enabled = project.screens.size > 1,
                        label = { Text(stringResource(R.string.action_delete)) },
                        modifier = Modifier.testTag("act_screen_delete")
                    )
                }
            }
        }

        item(key = "screens") {
            Column {
                SectionHeader(stringResource(R.string.screens_title, project.screens.size))
                project.screens.forEach { s ->
                    val selected = s.id == screen.id
                    val start = s.id == project.effectiveStartScreenId
                    Text(
                        text = (if (start) "★ " else "") + s.name + " · " +
                            stringResource(R.string.editor_elements_count, ElementTree.count(s.elements)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onSelectScreen(s.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("screen_row_${s.id}")
                    )
                }
            }
        }

        item(key = "preview") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader(stringResource(R.string.preview_title))
                Text(
                    text = stringResource(R.string.preview_current, dims(previewWidth, previewHeight)) +
                        if (atBase) "" else " — " + stringResource(R.string.preview_readonly_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = atBase,
                        onClick = { onSetPreview(0f, 0f) },
                        label = { Text(stringResource(R.string.preview_base, dims(screen.width, screen.height))) },
                        modifier = Modifier.testTag("preview_base")
                    )
                    PreviewSizes.presets.forEach { p ->
                        FilterChip(
                            selected = !atBase && p.width == previewWidth && p.height == previewHeight,
                            onClick = { onSetPreview(p.width, p.height) },
                            label = { Text(stringResource(p.labelRes) + " " + dims(p.width, p.height)) },
                            modifier = Modifier.testTag("preview_${p.id}")
                        )
                    }
                    AssistChip(
                        onClick = { onSetPreview(previewHeight, previewWidth) },
                        label = { Text(stringResource(R.string.preview_rotate)) },
                        modifier = Modifier.testTag("preview_rotate")
                    )
                }
            }
        }

        item(key = "design_size") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader(stringResource(R.string.design_size_title))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        stringResource(R.string.geo_width), screen.width.toInt(), 200, 1600,
                        { onSetScreenSize(it.toFloat(), screen.height) }, Modifier.weight(1f), "screen_w"
                    )
                    NumberField(
                        stringResource(R.string.geo_height), screen.height.toInt(), 200, 1600,
                        { onSetScreenSize(screen.width, it.toFloat()) }, Modifier.weight(1f), "screen_h"
                    )
                }
                Text(
                    text = stringResource(R.string.design_size_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "root_layout") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.screen_layout_title))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChildLayoutOptions.forEach { (layout, labelRes) ->
                        FilterChip(
                            selected = layout == screen.childLayout,
                            onClick = { onSetLayout(layout) },
                            label = { Text(stringResource(labelRes)) },
                            modifier = Modifier.testTag("screen_layout_${layout.name}")
                        )
                    }
                }
                screenDefs.forEach { def ->
                    PropertyEditorRow(def, screen.prop(def.key), onSetScreenProperty, Modifier.fillMaxWidth())
                }
                Text(
                    text = stringResource(R.string.screen_layout_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    when (dialog) {
        ScreenDialog.RENAME -> NameDialog(
            title = stringResource(R.string.dialog_rename_screen_title),
            initialName = screen.name,
            confirmLabel = stringResource(R.string.action_save),
            label = stringResource(R.string.screen_name_label),
            onConfirm = {
                onRename(it)
                dialog = null
            },
            onDismiss = { dialog = null }
        )

        ScreenDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.dialog_delete_screen_title)) },
            text = { Text(stringResource(R.string.dialog_delete_screen_message, screen.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        dialog = null
                    },
                    modifier = Modifier.testTag("confirm_delete_screen")
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )

        null -> Unit
    }
}
