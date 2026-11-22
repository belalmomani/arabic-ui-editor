package com.arabicuieditor.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.data.ai.AiClient
import com.arabicuieditor.data.ai.AiKeyStore
import com.arabicuieditor.data.ai.AiResult
import com.arabicuieditor.data.github.UrlConnectionHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** إرسال تلقائي اختياري: إن حُفظ مفتاح يُرسل [prompt] ويضع الرد عبر [onReply]؛ وإلا يبقى النسخ اليدوي كما هو. */
@Composable
fun AiAutoSection(prompt: String, enabled: Boolean, onReply: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    val settings = remember(tick) { AiKeyStore.load(context) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var detail by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    if (showKey) AiKeyDialog(onDismiss = { showKey = false }, onChanged = { tick++ })

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (settings != null) {
            OutlinedButton(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { AiClient.complete(UrlConnectionHttp(readTimeoutMs = 120_000), settings, prompt) }
                        busy = false
                        when (r) {
                            is AiResult.Ok -> onReply(r.text)
                            is AiResult.Fail -> {
                                detail = r.detail
                                error = when (r.reason) {
                                    AiResult.Reason.BAD_KEY -> R.string.aia_bad_key
                                    AiResult.Reason.RATE_LIMIT -> R.string.aia_rate
                                    AiResult.Reason.NETWORK -> R.string.aia_network
                                    else -> R.string.aia_other
                                }
                            }
                        }
                    }
                },
                enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().testTag("ai_auto")
            ) { Text(stringResource(if (busy) R.string.aia_busy else R.string.aia_send, settings.provider.label)) }
        }
        error?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { showKey = true }, modifier = Modifier.testTag("ai_key_open")) {
            Text(stringResource(if (settings == null) R.string.aik_open else R.string.aik_edit))
        }
    }
}
