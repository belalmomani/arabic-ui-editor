package com.arabicuieditor.ui.editor

import com.arabicuieditor.ui.theme.AppOutlinedButton
import com.arabicuieditor.ui.theme.AppButton
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.arabicuieditor.data.export.AndroidProjectExporter
import com.arabicuieditor.data.export.ComposeExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.arabicuieditor.data.model.Project

/** نافذة «كود Compose»: تعرض الكود المولَّد من المشروع (أو الشاشة الحالية) للنسخ أو المشاركة. */
@Composable
fun ExportCodeDialog(project: Project, currentScreenId: String?, onDismiss: () -> Unit) {
    var wholeProject by rememberSaveable { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    var format by rememberSaveable { mutableStateOf(0) } // 0 Compose، 1 XML، 2 Flutter
    val code = remember(project, wholeProject, currentScreenId, format) {
        val only = if (wholeProject) null else currentScreenId
        when (format) {
            1 -> com.arabicuieditor.data.export.XmlExporter.export(project, only)
            2 -> com.arabicuieditor.data.export.FlutterExporter.export(project, only)
            else -> ComposeExporter.export(project, only)
        }
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    // تصدير مشروع Android كامل كملف ZIP عبر منتقي الملفات (CreateDocument).
    var pkg by rememberSaveable { mutableStateOf(AndroidProjectExporter.DEFAULT_PACKAGE) }
    val highlighted = remember(code) { CodeHighlight.highlight(code) }
    val pkgValid = AndroidProjectExporter.isValidPackage(pkg)
    val imagesDir = com.arabicuieditor.ui.editor.canvas.LocalImageDir.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val zipLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val ok = try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        AndroidProjectExporter.writeZip(project, pkg, imagesDir, out) { id ->
                            // ملف الخط المخصص من موارد التطبيق (إن وُجد في res/font).
                            @Suppress("DiscouragedApi")
                            val res = context.resources.getIdentifier(id, "font", context.packageName)
                            if (res == 0) null else try { context.resources.openRawResource(res).use { it.readBytes() } } catch (_: Exception) { null }
                        }
                    } != null
                } catch (_: Exception) { false }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context, context.getString(if (ok) R.string.export_zip_ok else R.string.export_zip_failed),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("export_dialog")) {
            // شريطا الحالة والتنقل لا يغطيان الأزرار (النافذة بحجم الشاشة كلها).
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.export_title), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("export_close")) {
                        Text(stringResource(R.string.export_close))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = format == 0,
                        onClick = { format = 0; copied = false },
                        label = { Text(stringResource(R.string.export_fmt_compose)) },
                        modifier = Modifier.testTag("export_fmt_compose")
                    )
                    FilterChip(
                        selected = format == 1,
                        onClick = { format = 1; copied = false },
                        label = { Text(stringResource(R.string.export_fmt_xml)) },
                        modifier = Modifier.testTag("export_fmt_xml")
                    )
                    FilterChip(
                        selected = format == 2,
                        onClick = { format = 2; copied = false },
                        label = { Text(stringResource(R.string.export_fmt_flutter)) },
                        modifier = Modifier.testTag("export_fmt_flutter")
                    )
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
                // مشروع كامل قابل للبناء (Gradle + workflow) بدل لصق الكود يدويًا.
                androidx.compose.material3.OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it.trim().take(80) },
                    label = { Text(stringResource(R.string.export_pkg)) },
                    isError = !pkgValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("export_pkg")
                )
                AppButton(
                    onClick = { zipLauncher.launch(pkg.substringAfterLast('.') + "-android-project.zip") },
                    enabled = pkgValid,
                    modifier = Modifier.fillMaxWidth().testTag("export_project_zip")
                ) { Text(stringResource(R.string.export_zip_btn)) }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    // الكود يُعرض يسار-يمين دائمًا.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                            SelectionContainer {
                                Text(
                                    text = highlighted,
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
                    AppButton(
                        onClick = { clipboard.setText(AnnotatedString(code)); copied = true },
                        modifier = Modifier.testTag("export_copy")
                    ) { Text(stringResource(if (copied) R.string.export_copied else R.string.export_copy)) }
                    AppOutlinedButton(
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
