package com.arabicuieditor.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.data.ai.AiProject

/** «إنشاء بالذكاء الاصطناعي»: انسخ النص الجاهز إلى أي شات مجاني، ثم الصق الرد هنا. */
@Composable
fun AiProjectDialog(onCreate: (reply: String, onDone: (Boolean) -> Unit) -> Unit, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var idea by rememberSaveable { mutableStateOf("") }
    var reply by rememberSaveable { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ai_dialog"),
        title = { Text(stringResource(R.string.ai_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ai_step1), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = idea, onValueChange = { idea = it.take(600); copied = false },
                    label = { Text(stringResource(R.string.ai_idea_hint)) },
                    minLines = 2, modifier = Modifier.fillMaxWidth().testTag("ai_idea")
                )
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(AiProject.prompt(idea))); copied = true },
                    enabled = idea.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("ai_copy")
                ) { Text(stringResource(if (copied) R.string.ai_copied else R.string.ai_copy)) }
                Text(stringResource(R.string.ai_step2), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = reply, onValueChange = { reply = it.take(200_000); failed = false },
                    label = { Text(stringResource(R.string.ai_reply_hint)) },
                    minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth().testTag("ai_reply")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { reply = clipboard.getText()?.text.orEmpty().take(200_000); failed = false }) {
                        Text(stringResource(R.string.ai_paste))
                    }
                }
                if (failed) Text(stringResource(R.string.ai_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai_error"))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { busy = true; onCreate(reply) { ok -> busy = false; if (ok) onDismiss() else failed = true } },
                enabled = reply.isNotBlank() && !busy, modifier = Modifier.testTag("ai_create")
            ) { Text(stringResource(R.string.ai_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_close)) } }
    )
}
