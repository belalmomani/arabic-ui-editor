package com.arabicuieditor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arabicuieditor.R
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.newId
import com.arabicuieditor.ui.common.formatDateTime
import com.arabicuieditor.ui.screens.AppTopBar

/**
 * هيكل المحرر في هذه المرحلة: يفتح المشروع المحفوظ ويعرض بياناته،
 * ويسمح بإضافة شاشة لاختبار الحفظ التلقائي. السحب والإفلات في مرحلة لاحقة.
 */
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val saveFailed by viewModel.saveFailed.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val title = (state as? EditorState.Ready)?.project?.name ?: stringResource(R.string.editor_title)

    Scaffold(
        topBar = {
            AppTopBar(title = title, onBack = {
                viewModel.flush()
                onBack()
            })
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                EditorState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                EditorState.NotFound -> Text(
                    text = stringResource(R.string.editor_not_found),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center
                )
                is EditorState.Ready -> ProjectContent(
                    project = s.project,
                    saveFailed = saveFailed,
                    onAddScreen = {
                        viewModel.edit { p ->
                            val number = p.screens.size + 1
                            val name = context.getString(R.string.editor_new_screen_name, number)
                            p.copy(screens = p.screens + UiScreen(id = newId(), name = name))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProjectContent(
    project: Project,
    saveFailed: Boolean,
    onAddScreen: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.editor_last_modified, formatDateTime(project.updatedAt)),
            style = MaterialTheme.typography.bodyMedium
        )
        if (saveFailed) {
            Text(
                text = stringResource(R.string.editor_save_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.editor_screens_header, project.screens.size),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(project.screens, key = { it.id }) { screen ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(screen.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = stringResource(R.string.editor_elements_count, screen.elements.size),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAddScreen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.editor_add_screen))
        }
    }
}
