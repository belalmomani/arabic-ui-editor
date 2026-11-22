package com.arabicuieditor.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arabicuieditor.R
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.ui.common.formatDateTime
import com.arabicuieditor.ui.screens.AppTopBar

@Composable
fun ProjectsScreen(
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenSetup: (String) -> Unit = onOpenProject,
    onOpenFill: (String) -> Unit = onOpenProject,
    viewModel: ProjectsViewModel = viewModel()
) {
    val projects by viewModel.projects.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val error by viewModel.error.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    var showPlanner by remember { mutableStateOf(false) }
    var fillTarget by remember { mutableStateOf<Project?>(null) }
    var renameTarget by remember { mutableStateOf<Project?>(null) }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }

    var zipTarget by remember { mutableStateOf<Project?>(null) }
    val zipSaver = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val t = zipTarget
        if (uri != null && t != null) viewModel.exportZip(t.id, uri)
        zipTarget = null
    }
    var blueprintTarget by remember { mutableStateOf<Project?>(null) }
    val blueprintSaver = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val t = blueprintTarget
        if (uri != null && t != null) viewModel.exportBlueprint(t.id, uri)
        blueprintTarget = null
    }
    val zipPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importZip(uri) }
    val driveLinked by viewModel.driveLinked.collectAsState()
    val info by viewModel.info.collectAsState()
    val infoText = info?.let { stringResource(it) }
    var driveTarget by remember { mutableStateOf<Project?>(null) }
    val driveFolderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.linkDrive(uri, driveTarget?.id)
        driveTarget = null
    }
    val driveAuto by viewModel.driveAuto.collectAsState()
    val driveFiles by viewModel.driveFiles.collectAsState()
    driveFiles?.let { files ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::closeDriveList,
            title = { Text(stringResource(R.string.drive_import_title)) },
            text = {
                if (files.isEmpty()) Text(stringResource(R.string.drive_import_empty))
                else LazyColumn(Modifier.testTag("drive_files")) {
                    items(files.size) { i ->
                        Text(
                            files[i].first,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { viewModel.importFromDrive(files[i].second) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = viewModel::closeDriveList) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText = error?.let { stringResource(it) }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    var pendingIds by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) { viewModel.refresh(); pendingIds = com.arabicuieditor.data.planner.SetupStore.pending(ctx) }
    val pendingProject = projects.firstOrNull { it.id in pendingIds }
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "pulseScale"
    )
    LaunchedEffect(infoText) {
        if (infoText != null) {
            snackbarHostState.showSnackbar(infoText)
            viewModel.clearInfo()
        }
    }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.projects_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pendingProject != null) ExtendedFloatingActionButton(
                    onClick = { onOpenFill(pendingProject.id) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }.testTag("continue_setup")
                ) { Text(stringResource(R.string.fill_pulse_btn)) }
                if (driveLinked) ExtendedFloatingActionButton(
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(1.dp, 1.dp, 1.dp, 1.dp),
                    onClick = viewModel::openDriveList,
                    modifier = Modifier.testTag("import_drive")
                ) { Text(stringResource(R.string.drive_import)) }
                ExtendedFloatingActionButton(
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(1.dp, 1.dp, 1.dp, 1.dp),
                    onClick = { zipPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    modifier = Modifier.testTag("import_zip")
                ) { Text(stringResource(R.string.import_zip)) }
                ExtendedFloatingActionButton(
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(1.dp, 1.dp, 1.dp, 1.dp),
                    onClick = { viewModel.importText(clipboard.getText()?.text.orEmpty()) },
                    modifier = Modifier.testTag("import_project")
                ) { Text(stringResource(R.string.import_project)) }
                ExtendedFloatingActionButton(
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(1.dp, 1.dp, 1.dp, 1.dp),
                    onClick = { showPlanner = true },
                    modifier = Modifier.testTag("create_planner")
                ) { Text(stringResource(R.string.planner_fab)) }
                ExtendedFloatingActionButton(
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(1.dp, 1.dp, 1.dp, 1.dp),
                    onClick = { showAi = true },
                    modifier = Modifier.testTag("create_ai")
                ) { Text(stringResource(R.string.ai_fab)) }
                ExtendedFloatingActionButton(onClick = { showCreate = true }, elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(1.dp, 1.dp, 1.dp, 1.dp)) {
                    Text(stringResource(R.string.home_new_project))
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loaded && projects.isEmpty()) {
                Text(
                    text = stringResource(R.string.projects_empty),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onOpen = { onOpenProject(project.id) },
                            onRename = { renameTarget = project },
                            onFill = { fillTarget = project },
                            onBlueprint = {
                                blueprintTarget = project
                                blueprintSaver.launch(project.name.replace(Regex("[^\\p{L}\\p{N}_ -]"), "_").ifBlank { "project" } + "-blueprint.md")
                            },
                            onDuplicate = { viewModel.duplicate(project) },
                            onZip = {
                                zipTarget = project
                                zipSaver.launch(project.name.replace(Regex("[^\\p{L}\\p{N}_ -]"), "_").ifBlank { "project" } + ".zip")
                            },
                            driveLinked = driveLinked,
                            driveAuto = driveAuto,
                            onDriveAuto = viewModel::toggleDriveAuto,
                            onDrive = {
                                if (driveLinked) viewModel.uploadToDrive(project.id)
                                else { driveTarget = project; driveFolderPicker.launch(null) }
                            },
                            onDriveChange = { driveTarget = null; driveFolderPicker.launch(null) },
                            onDriveUnlink = viewModel::unlinkDrive,
                            onBackup = {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, com.arabicuieditor.data.storage.ProjectJsonCodec.encode(project))
                                }
                                try {
                                    context.startActivity(android.content.Intent.createChooser(send, null))
                                } catch (_: Exception) {
                                }
                            },
                            onDelete = { deleteTarget = project }
                        )
                    }
                }
            }
        }
    }

    fillTarget?.let { target ->
        AiContentDialog(
            project = target,
            onApply = { reply, onDone -> viewModel.applyAiContent(target.id, reply, onDone) },
            onDismiss = { fillTarget = null }
        )
    }

    if (showPlanner) PlannerDialog(onCreate = { plan -> viewModel.createPlanned(plan, onCreated = onOpenSetup) }, onDismiss = { showPlanner = false })

    if (showAi) AiProjectDialog(onCreate = viewModel::importAi, onDismiss = { showAi = false })

    if (showCreate) {
        NameDialog(
            title = stringResource(R.string.dialog_new_project_title),
            initialName = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                showCreate = false
                viewModel.create(name, onCreated = onOpenProject)
            },
            onDismiss = { showCreate = false }
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = stringResource(R.string.dialog_rename_title),
            initialName = target.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name ->
                renameTarget = null
                viewModel.rename(target.id, name)
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            projectName = target.name,
            onConfirm = {
                deleteTarget = null
                viewModel.delete(target.id)
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onFill: () -> Unit,
    onBlueprint: () -> Unit,
    onDuplicate: () -> Unit,
    onBackup: () -> Unit,
    onZip: () -> Unit,
    driveLinked: Boolean,
    driveAuto: Boolean,
    onDriveAuto: () -> Unit,
    onDrive: () -> Unit,
    onDriveChange: () -> Unit,
    onDriveUnlink: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = stringResource(
                        R.string.project_meta,
                        formatDateTime(project.updatedAt),
                        project.screens.size
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_more)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_open)) },
                        onClick = { menuOpen = false; onOpen() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_ai_fill)) },
                        onClick = { menuOpen = false; onFill() },
                        modifier = Modifier.testTag("menu_ai_fill")
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_blueprint)) },
                        onClick = { menuOpen = false; onBlueprint() },
                        modifier = Modifier.testTag("menu_blueprint")
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_duplicate)) },
                        onClick = { menuOpen = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_backup)) },
                        onClick = { menuOpen = false; onBackup() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_zip)) },
                        onClick = { menuOpen = false; onZip() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_drive_upload)) },
                        onClick = { menuOpen = false; onDrive() },
                        modifier = Modifier.testTag("menu_drive_upload")
                    )
                    if (driveLinked) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (driveAuto) R.string.action_drive_auto_off else R.string.action_drive_auto_on)) },
                            onClick = { menuOpen = false; onDriveAuto() },
                            modifier = Modifier.testTag("menu_drive_auto")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_drive_change)) },
                            onClick = { menuOpen = false; onDriveChange() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_drive_unlink)) },
                            onClick = { menuOpen = false; onDriveUnlink() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}
