package com.arabicuieditor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arabicuieditor.R
import com.arabicuieditor.ui.projects.NameDialog
import com.arabicuieditor.ui.projects.ProjectsViewModel

@Composable
fun HomeScreen(
    onOpenProject: (String) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenAbout: () -> Unit,
    projectsViewModel: ProjectsViewModel = viewModel()
) {
    var showCreate by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
            Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_new_project))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_my_projects))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_about))
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = stringResource(R.string.dialog_new_project_title),
            initialName = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                showCreate = false
                projectsViewModel.create(name, onCreated = onOpenProject)
            },
            onDismiss = { showCreate = false }
        )
    }
}
