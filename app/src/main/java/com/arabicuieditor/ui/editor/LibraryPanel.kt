package com.arabicuieditor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.arabicuieditor.data.storage.UserComponent
import com.arabicuieditor.data.storage.UserLibraryRepository
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
import com.arabicuieditor.data.model.AppTemplate
import com.arabicuieditor.data.model.PresetCategory
import com.arabicuieditor.data.model.TemplateCategory
import com.arabicuieditor.data.model.Templates

/** مكتبة المكونات الجاهزة: فئات في الأعلى، ومكوّنات الفئة المختارة في صف أفقي. نقرة تضيف المكوّن. */
@Composable
fun LibraryPanel(
    onAdd: (ComponentPreset) -> Unit,
    modifier: Modifier = Modifier,
    userComponents: List<UserComponent> = emptyList(),
    canSaveSelected: Boolean = false,
    selectedName: String = "",
    onSaveSelected: (String) -> Unit = {},
    onAddUser: (UserComponent) -> Unit = {},
    onRenameUser: (String, String) -> Unit = { _, _ -> },
    onDeleteUser: (String) -> Unit = {},
    onUseTemplate: (AppTemplate) -> Unit = {}
) {
    // الفهرس -1 = «مكتبتي» (المكوّنات الشخصية)، وما بعده فئات المكتبة الجاهزة.
    var categoryIndex by rememberSaveable { mutableIntStateOf(0) }
    val categories = PresetCategory.entries
    val mine = categoryIndex == -1
    val templates = categoryIndex == -2
    val category = categories[categoryIndex.coerceIn(0, categories.lastIndex)]
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = templates,
                onClick = { categoryIndex = -2 },
                label = { Text(stringResource(R.string.tpl_tab)) },
                modifier = Modifier.testTag("cat_TEMPLATES")
            )
            FilterChip(
                selected = mine,
                onClick = { categoryIndex = -1 },
                label = { Text(stringResource(R.string.lib_mine)) },
                modifier = Modifier.testTag("cat_MINE")
            )
            categories.forEachIndexed { index, c ->
                FilterChip(
                    selected = !mine && !templates && c == category,
                    onClick = { categoryIndex = index },
                    label = { Text(stringResource(c.labelRes)) },
                    modifier = Modifier.testTag("cat_${c.name}")
                )
            }
        }
        if (templates) {
            TemplatesSection(onUseTemplate)
        } else if (mine) {
            MineSection(
                userComponents, canSaveSelected,
                onSave = { nameDialog = NameDialog(null, selectedName) },
                onAddUser = onAddUser,
                onRename = { c -> nameDialog = NameDialog(c.id, c.name) },
                onDelete = onDeleteUser
            )
        } else {
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

    nameDialog?.let { d ->
        NameDialogView(
            initial = d.initial,
            onDismiss = { nameDialog = null },
            onConfirm = { name ->
                if (d.id == null) onSaveSelected(name) else onRenameUser(d.id, name)
                nameDialog = null
            }
        )
    }
}

private class NameDialog(val id: String?, val initial: String)

@Composable
private fun MineSection(
    items: List<UserComponent>,
    canSave: Boolean,
    onSave: () -> Unit,
    onAddUser: (UserComponent) -> Unit,
    onRename: (UserComponent) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.testTag("lib_save_selected")
        ) {
            Text(stringResource(if (canSave) R.string.lib_save_selected else R.string.lib_save_hint))
        }
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.lib_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("lib_empty")
            )
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items.forEach { c ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.width(132.dp).testTag("user_${c.id}")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clickable { onAddUser(c) }
                                    .testTag("user_add_${c.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    c.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                            Row {
                                TextButton(onClick = { onRename(c) }) {
                                    Text(stringResource(R.string.action_rename), style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(
                                    onClick = { onDelete(c.id) },
                                    modifier = Modifier.testTag("user_delete_${c.id}")
                                ) {
                                    Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NameDialogView(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial.take(UserLibraryRepository.MAX_NAME_LENGTH)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lib_name)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(UserLibraryRepository.MAX_NAME_LENGTH) },
                singleLine = true,
                modifier = Modifier.testTag("lib_name_field")
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("lib_name_ok")
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun TemplatesSection(onUse: (AppTemplate) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableIntStateOf(-1) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val category = TemplateCategory.entries.getOrNull(cat)
    val shown = Templates.filter(category, query) { context.getString(it.labelRes) + " " + context.getString(it.descRes) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.tpl_search)) },
            modifier = Modifier.fillMaxWidth().testTag("tpl_search")
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = cat == -1, onClick = { cat = -1 },
                label = { Text(stringResource(R.string.tpl_all)) }, modifier = Modifier.testTag("tplcat_ALL"))
            TemplateCategory.entries.forEachIndexed { i, c ->
                FilterChip(selected = cat == i, onClick = { cat = i },
                    label = { Text(stringResource(c.labelRes)) }, modifier = Modifier.testTag("tplcat_${c.name}"))
            }
        }
        if (shown.isEmpty()) {
            Text(stringResource(R.string.tpl_none), style = MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                shown.forEach { t ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.width(190.dp).testTag("tpl_${t.id}")
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(t.labelRes), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(t.descRes), style = MaterialTheme.typography.bodySmall, maxLines = 3)
                            Button(onClick = { onUse(t) }, modifier = Modifier.testTag("tpl_use_${t.id}")) {
                                Text(stringResource(R.string.tpl_use))
                            }
                        }
                    }
                }
            }
        }
    }
}
