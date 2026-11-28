package com.arabicuieditor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arabicuieditor.R
import com.arabicuieditor.data.export.DesktopHtmlExporter
import com.arabicuieditor.data.export.WindowsProjectExporter
import com.arabicuieditor.data.model.Platforms
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.ui.theme.AppButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** نافذة تصدير مشاريع Windows: تعرض الملف الرئيسي وتحفظ المشروع ZIP. تصميم واجهة فقط. */
@Composable
fun WindowsExportDialog(project: Project, onDismiss: () -> Unit) {
    var rtl by rememberSaveable { mutableStateOf(true) }
    var scriptIndex by rememberSaveable { mutableStateOf(1) } // 0 بلا، 1 JS، 2 TS
    val script = DesktopHtmlExporter.Script.values()[scriptIndex]
    val options = WindowsProjectExporter.Options(script, rtl)
    val code = remember(project, rtl, scriptIndex) { WindowsProjectExporter.preview(project, options) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagesDir = com.arabicuieditor.ui.editor.canvas.LocalImageDir.current
    val isHtml = project.uiTech == Platforms.TECH_HTML
    val zipLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val ok = try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        WindowsProjectExporter.writeZip(project, imagesDir, out, options)
                    } != null
                } catch (_: Exception) { false }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context, context.getString(if (ok) R.string.win_export_ok else R.string.export_zip_failed),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    if (ok) com.arabicuieditor.ads.AdsManager.showInterstitial(context)
                }
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("win_export_dialog")) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.win_export_title, stringResource(com.arabicuieditor.ui.projects.techLabel(project.uiTech))), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("win_export_close")) { Text(stringResource(R.string.export_close)) }
                }
                Text(stringResource(R.string.windows_design_only_note), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = rtl, onClick = { rtl = true }, label = { Text(stringResource(R.string.win_dir_rtl)) }, modifier = Modifier.testTag("win_dir_rtl"))
                    FilterChip(selected = !rtl, onClick = { rtl = false }, label = { Text(stringResource(R.string.win_dir_ltr)) }, modifier = Modifier.testTag("win_dir_ltr"))
                }
                if (isHtml) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = scriptIndex == 0, onClick = { scriptIndex = 0 }, label = { Text(stringResource(R.string.win_script_none)) }, modifier = Modifier.testTag("win_script_none"))
                        FilterChip(selected = scriptIndex == 1, onClick = { scriptIndex = 1 }, label = { Text("JavaScript") }, modifier = Modifier.testTag("win_script_js"))
                        FilterChip(selected = scriptIndex == 2, onClick = { scriptIndex = 2 }, label = { Text("TypeScript") }, modifier = Modifier.testTag("win_script_ts"))
                    }
                }
                AppButton(
                    onClick = { zipLauncher.launch(WindowsProjectExporter.zipName(project)) },
                    modifier = Modifier.fillMaxWidth().testTag("win_export_zip")
                ) { Text(stringResource(R.string.win_export_zip_btn)) }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                            SelectionContainer {
                                Text(
                                    text = code,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(8.dp).testTag("win_export_code")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
