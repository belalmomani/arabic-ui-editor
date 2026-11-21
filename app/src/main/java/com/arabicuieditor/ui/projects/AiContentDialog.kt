package com.arabicuieditor.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.arabicuieditor.data.ai.AiContent
import com.arabicuieditor.data.model.Project

/** تعبئة نصوص مشروع موجود: انسخ الطلب القصير إلى شات مجاني ثم الصق الرد. */
@Composable
fun AiContentDialog(project: Project, onApply: (reply: String, onDone: (Int) -> Unit) -> Unit, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var idea by rememberSaveable { mutableStateOf("") }
    var reply by rememberSaveable { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    val count = remember(project) { AiContent.slots(project).size }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ai_content_dialog"),
        title = { Text(stringResource(R.string.aic_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.aic_step1, count), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = idea, onValueChange = { idea = it.take(400); copied = false },
                    label = { Text(stringResource(R.string.aic_idea_hint)) }, minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("aic_idea")
                )
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(AiContent.prompt(project, idea))); copied = true },
                    enabled = count > 0, modifier = Modifier.fillMaxWidth().testTag("aic_copy")
                ) { Text(stringResource(if (copied) R.string.ai_copied else R.string.ai_copy)) }
                Text(stringResource(R.string.ai_step2), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = reply, onValueChange = { reply = it.take(200_000); result = null },
                    label = { Text(stringResource(R.string.ai_reply_hint)) }, minLines = 3, maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("aic_reply")
                )
                OutlinedButton(onClick = { reply = clipboard.getText()?.text.orEmpty().take(200_000); result = null }) {
                    Text(stringResource(R.string.ai_paste))
                }
                result?.let { n ->
                    if (n == 0) Text(stringResource(R.string.aic_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("aic_error"))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(reply) { n -> if (n > 0) onDismiss() else result = 0 } },
                enabled = reply.isNotBlank(), modifier = Modifier.testTag("aic_apply")
            ) { Text(stringResource(R.string.aic_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_close)) } }
    )
}
