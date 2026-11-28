package com.arabicuieditor.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.arabicuieditor.data.model.Platforms
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.arabicuieditor.R
import com.arabicuieditor.data.storage.ProjectRepository

/** حوار إدخال اسم (إنشاء مشروع أو إعادة تسميته). */
@Composable
fun NameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    /** نص عنوان الحقل؛ الافتراضي «اسم المشروع». */
    label: String? = null
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= ProjectRepository.MAX_NAME_LENGTH) text = it },
                label = { Text(label ?: stringResource(R.string.project_name_label)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** حوار تأكيد الحذف. */
@Composable
fun ConfirmDeleteDialog(
    projectName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_title)) },
        text = { Text(stringResource(R.string.dialog_delete_message, projectName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}


/**
 * حوار مشروع جديد: الاسم + المنصة (Android افتراضيًا أو Windows Desktop UI) + تقنية التصميم لـ Windows.
 * اختيار Windows مستقل: لا يغيّر خيارات Android.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProjectDialog(
    onConfirm: (name: String, platform: String, tech: String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf(Platforms.ANDROID) }
    var tech by remember { mutableStateOf(Platforms.TECH_WINUI) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_new_project_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= ProjectRepository.MAX_NAME_LENGTH) text = it },
                    label = { Text(stringResource(R.string.project_name_label)) },
                    singleLine = true
                )
                Text(stringResource(R.string.platform_label))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = platform == Platforms.ANDROID,
                        onClick = { platform = Platforms.ANDROID },
                        label = { Text(stringResource(R.string.platform_android)) },
                        modifier = Modifier.testTag("platform_android")
                    )
                    FilterChip(
                        selected = platform == Platforms.WINDOWS,
                        onClick = { platform = Platforms.WINDOWS },
                        label = { Text(stringResource(R.string.platform_windows)) },
                        modifier = Modifier.testTag("platform_windows")
                    )
                }
                if (platform == Platforms.WINDOWS) {
                    Text(stringResource(R.string.windows_tech_label))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Platforms.WINDOWS_TECHS.forEach { t ->
                            FilterChip(
                                selected = tech == t,
                                onClick = { tech = t },
                                label = { Text(stringResource(techLabel(t))) },
                                modifier = Modifier.testTag("tech_$t")
                            )
                        }
                    }
                    Text(stringResource(R.string.windows_design_only_note))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim(), platform, if (platform == Platforms.WINDOWS) tech else "") }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

fun techLabel(tech: String): Int = when (tech) {
    Platforms.TECH_WINUI -> R.string.tech_winui
    Platforms.TECH_WPF -> R.string.tech_wpf
    Platforms.TECH_HTML -> R.string.tech_html
    else -> R.string.tech_flutter
}
