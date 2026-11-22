package com.arabicuieditor.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.data.ai.AiKeyStore
import com.arabicuieditor.data.ai.AiProvider
import com.arabicuieditor.data.ai.AiSettings

/** إعداد مفتاح الذكاء الاصطناعي (اختياري): يُحفظ على الجهاز فقط ويُستعمل للإرسال التلقائي. */
@Composable
fun AiKeyDialog(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val saved = remember { AiKeyStore.load(context) }
    var providerId by rememberSaveable { mutableStateOf(saved?.providerId ?: "gemini") }
    var key by rememberSaveable { mutableStateOf(saved?.apiKey.orEmpty()) }
    var model by rememberSaveable { mutableStateOf(saved?.model.orEmpty()) }
    var base by rememberSaveable { mutableStateOf(saved?.customBaseUrl.orEmpty()) }
    val provider = AiProvider.byId(providerId)
    val settings = AiSettings(providerId, key, model, base)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ai_key_dialog"),
        title = { Text(stringResource(R.string.aik_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.aik_intro), style = MaterialTheme.typography.bodySmall)
                AiProvider.all.forEach { p ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (p.keyPage.isNotEmpty()) Text(
                            text = stringResource(R.string.aik_get_key, p.keyPage.removePrefix("https://").trimEnd('/')),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                try { uriHandler.openUri(p.keyPage) } catch (_: Exception) { }
                            }.testTag("aik_link_${p.id}")
                        )
                        FilterChip(
                            selected = p.id == providerId,
                            onClick = { providerId = p.id; model = "" },
                            label = { Text(p.label + if (p.free) " ✓" else "") },
                            modifier = Modifier.fillMaxWidth().testTag("aik_provider_${p.id}")
                        )
                    }
                }
                Text(stringResource(R.string.aik_free_note), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = key, onValueChange = { key = it.trim().take(400) },
                    label = { Text(stringResource(R.string.aik_key)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("aik_key")
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it.trim().take(120) },
                    label = { Text(stringResource(R.string.aik_model, provider.defaultModel)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("aik_model")
                )
                if (providerId == AiProvider.CUSTOM) OutlinedTextField(
                    value = base, onValueChange = { base = it.trim().take(200) },
                    label = { Text(stringResource(R.string.aik_base)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("aik_base")
                )
                Text(stringResource(R.string.aik_privacy), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { AiKeyStore.save(context, settings); onChanged(); onDismiss() },
                enabled = settings.isUsable, modifier = Modifier.testTag("aik_save")
            ) { Text(stringResource(R.string.aik_save)) }
        },
        dismissButton = {
            Row {
                if (saved != null) TextButton(
                    onClick = { AiKeyStore.clear(context); onChanged(); onDismiss() },
                    modifier = Modifier.testTag("aik_clear")
                ) { Text(stringResource(R.string.aik_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_close)) }
            }
        }
    )
}
