package com.arabicuieditor.ui.editor

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arabicuieditor.R
import com.arabicuieditor.data.export.ComposeExporter
import com.arabicuieditor.data.model.Project

/** نافذة «كود Compose»: تعرض الكود المولَّد من المشروع (أو الشاشة الحالية) للنسخ أو المشاركة. */
@Composable
fun ExportCodeDialog(project: Project, currentScreenId: String?, onDismiss: () -> Unit) {
    var wholeProject by rememberSaveable { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    val code = remember(project, wholeProject, currentScreenId) {
        ComposeExporter.export(project, if (wholeProject) null else currentScreenId)
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("export_dialog")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.export_title), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("export_close")) {
                        Text(stringResource(R.string.export_close))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = wholeProject,
                        onClick = { wholeProject = true; copied = false },
                        label = { Text(stringResource(R.string.export_scope_project)) },
                        modifier = Modifier.testTag("export_scope_project")
                    )
                    FilterChip(
                        selected = !wholeProject,
                        onClick = { wholeProject = false; copied = false },
                        enabled = currentScreenId != null,
                        label = { Text(stringResource(R.string.export_scope_screen)) },
                        modifier = Modifier.testTag("export_scope_screen")
                    )
                }
                Text(stringResource(R.string.export_note), style = MaterialTheme.typography.bodySmall)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    // الكود يُعرض يسار-يمين دائمًا.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                            SelectionContainer {
                                Text(
                                    text = code,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(8.dp).testTag("export_code")
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { clipboard.setText(AnnotatedString(code)); copied = true },
                        modifier = Modifier.testTag("export_copy")
                    ) { Text(stringResource(if (copied) R.string.export_copied else R.string.export_copy)) }
                    OutlinedButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, code)
                            }
                            try {
                                context.startActivity(Intent.createChooser(send, null))
                            } catch (_: Exception) {
                                // لا تطبيق يستقبل النص أو حجمه كبير: يبقى النسخ متاحًا.
                            }
                        },
                        modifier = Modifier.testTag("export_share")
                    ) { Text(stringResource(R.string.export_share)) }
                }
            }
        }
    }
}
