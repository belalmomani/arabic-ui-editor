package com.arabicuieditor.ui.editor

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.data.export.AndroidProjectExporter
import com.arabicuieditor.data.github.BuildState
import com.arabicuieditor.data.github.GitHubApi
import com.arabicuieditor.data.github.GitHubAuthStore
import com.arabicuieditor.data.github.GitHubBuilder
import com.arabicuieditor.data.github.GitHubException
import com.arabicuieditor.data.github.UrlConnectionHttp
import com.arabicuieditor.data.model.Project
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** «ابنِ تطبيقي»: يرفع المشروع إلى مستودع خاص في حساب المستخدم ويشغّل البناء ثم يعرض رابط النتيجة. */
@Composable
fun GitHubBuildDialog(project: Project, packageName: String, imagesDir: File?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clientId = stringResource(R.string.github_client_id).trim()
    var state by remember { mutableStateOf<BuildState?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        if (clientId.isEmpty()) return@LaunchedEffect
        val builder = GitHubBuilder(GitHubApi(UrlConnectionHttp()))
        val onState: (BuildState) -> Unit = { state = it }
        val result = withContext(Dispatchers.IO) {
            try {
                var token = GitHubAuthStore.token(context)
                if (token == null) {
                    token = builder.authorize(clientId, onState)
                    GitHubAuthStore.save(context, token)
                }
                val files = AndroidProjectExporter.collectFiles(
                    project, packageName, imagesDir,
                    fontLoader = { id ->
                        @Suppress("DiscouragedApi")
                        val res = context.resources.getIdentifier(id, "font", context.packageName)
                        if (res == 0) null else try { context.resources.openRawResource(res).use { it.readBytes() } } catch (_: Exception) { null }
                    },
                    stickerLoader = { n -> try { context.assets.open(com.arabicuieditor.data.model.Stickers.assetPath(n)).use { it.readBytes() } } catch (_: Exception) { null } },
                    soundLoader = { id -> try { context.assets.open(com.arabicuieditor.data.model.Sounds.assetPath(id)).use { it.readBytes() } } catch (_: Exception) { null } }
                )
                val r = builder.build(token, GitHubApi.repoName(project.name, project.id), files, onState)
                if (r is BuildState.Failed && r.unauthorized) GitHubAuthStore.clear(context)
                r
            } catch (e: GitHubException) {
                BuildState.Failed(e.message.orEmpty())
            } catch (e: java.io.IOException) {
                BuildState.Failed(context.getString(R.string.gh_network_error))
            }
        }
        state = result
    }

    val st = state
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("github_dialog"),
        title = { Text(stringResource(R.string.gh_build_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when {
                    clientId.isEmpty() -> Text(stringResource(R.string.gh_no_client_id))
                    st == null -> Text(stringResource(R.string.gh_starting))
                    st is BuildState.NeedAuth -> {
                        Text(stringResource(R.string.gh_enter_code))
                        SelectionContainer {
                            Text(st.userCode, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("github_user_code"))
                        }
                        TextButton(onClick = { openUrl(context, st.uri) }) { Text(stringResource(R.string.gh_open_github)) }
                    }
                    st is BuildState.Pushing -> Text(stringResource(R.string.gh_pushing, st.done, st.total))
                    st is BuildState.Building -> {
                        Text(stringResource(R.string.gh_building))
                        st.runUrl?.let { u -> TextButton(onClick = { openUrl(context, u) }) { Text(stringResource(R.string.gh_open_run)) } }
                    }
                    st is BuildState.Done -> {
                        Text(stringResource(if (st.success) R.string.gh_done_ok else R.string.gh_done_fail))
                        TextButton(onClick = { openUrl(context, st.runUrl) }, modifier = Modifier.padding(0.dp)) {
                            Text(stringResource(R.string.gh_open_run))
                        }
                    }
                    st is BuildState.Failed -> Text(stringResource(R.string.gh_failed, st.reason))
                }
            }
        },
        confirmButton = {
            if (st is BuildState.Failed) TextButton(onClick = { state = null; attempt++ }) { Text(stringResource(R.string.gh_retry)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_close)) }
        },
        dismissButton = if (st is BuildState.Failed) ({ TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_close)) } }) else null
    )
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}
