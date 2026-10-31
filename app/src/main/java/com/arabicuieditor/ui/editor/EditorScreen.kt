package com.arabicuieditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arabicuieditor.R
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.ui.editor.canvas.CanvasMath
import com.arabicuieditor.ui.editor.canvas.DesignCanvas
import com.arabicuieditor.ui.editor.canvas.DesignRect
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import com.arabicuieditor.ui.editor.canvas.SnapSettings
import com.arabicuieditor.ui.editor.canvas.Viewport
import com.arabicuieditor.ui.screens.AppTopBar
import kotlin.math.roundToInt

private const val FIT_MARGIN_DP = 16f
private const val CASCADE_STEP_DP = 14f
private const val PANEL_HEIGHT_DP = 200

private enum class BottomTab(val labelRes: Int) {
    ELEMENTS(R.string.tab_elements),
    LIBRARY(R.string.tab_library),
    PROPERTIES(R.string.tab_properties),
    LAYERS(R.string.tab_layers),
    SCREEN(R.string.tab_screen),
    DESIGN(R.string.tab_design)
}

/** عنصر يُسحب الآن من القائمة (للعرض الشبحي ولتحديد مكان الإفلات). */
private data class PaletteDrag(val type: String, val windowPos: Offset)

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val saveFailed by viewModel.saveFailed.collectAsState()
    val currentScreenId by viewModel.currentScreenId.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ready = state as? EditorState.Ready
    val title = ready?.project?.name ?: stringResource(R.string.editor_title)

    var playing by rememberSaveable { mutableStateOf(false) }
    var exporting by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = title,
                onBack = {
                    viewModel.flush()
                    onBack()
                },
                actions = {
                    TextButton(
                        onClick = { playing = true },
                        enabled = ready != null,
                        modifier = Modifier.testTag("btn_play")
                    ) { Text(stringResource(R.string.proto_play)) }
                    TextButton(
                        onClick = { exporting = true },
                        enabled = ready != null,
                        modifier = Modifier.testTag("btn_export")
                    ) { Text(stringResource(R.string.export_btn)) }
                    TextButton(onClick = viewModel::undo, enabled = canUndo, modifier = Modifier.testTag("btn_undo")) {
                        Text(stringResource(R.string.action_undo))
                    }
                    TextButton(onClick = viewModel::redo, enabled = canRedo, modifier = Modifier.testTag("btn_redo")) {
                        Text(stringResource(R.string.action_redo))
                    }
                    if (selectedId != null) {
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.editor_delete_element)
                            )
                        }
                    }
                }
            )
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

                is EditorState.Ready -> EditorContent(
                    project = s.project,
                    currentScreenId = currentScreenId,
                    selectedId = selectedId,
                    saveFailed = saveFailed,
                    viewModel = viewModel
                )
            }
        }
    }
    if (exporting && ready != null) {
        ExportCodeDialog(
            project = ready.project,
            currentScreenId = currentScreenId,
            onDismiss = { exporting = false }
        )
    }
    if (playing && ready != null) {
        val startId = currentScreenId?.takeIf { id -> ready.project.screens.any { it.id == id } }
            ?: ready.project.effectiveStartScreenId
        if (startId != null) {
            PrototypePlayer(
                project = ready.project,
                startScreenId = startId,
                designSystem = ready.project.designSystem,
                onClose = { playing = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    }
}

@Composable
private fun EditorContent(
    project: Project,
    currentScreenId: String?,
    selectedId: String?,
    saveFailed: Boolean,
    viewModel: EditorViewModel
) {
    val screen: UiScreen = project.screens.firstOrNull { it.id == currentScreenId }
        ?: project.screens.firstOrNull()
        ?: return
    val userComponents by viewModel.userComponents.collectAsState()
    val density = LocalDensity.current.density
    val (previewW, previewH) = project.previewSizeFor(screen)
    // التخطيط يُحسب عند حجم المعاينة؛ السحب والتحجيم متاحان فقط عند حجم التصميم نفسه.
    val layout = remember(screen, previewW, previewH) { LayoutEngine.layout(screen, previewW, previewH) }
    val editable = previewW == screen.width && previewH == screen.height

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var canvasBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var rootWindowPos by remember { mutableStateOf(Offset.Zero) }
    // العرض (تكبير/إزاحة) لكل شاشة على حدة؛ null يعني «لم يُضبط بعد، ضعه بالملاءمة».
    var viewport by remember(screen.id, previewW, previewH) { mutableStateOf<Viewport?>(null) }
    var drag by remember { mutableStateOf<PaletteDrag?>(null) }
    var openTab by rememberSaveable { mutableStateOf<BottomTab?>(BottomTab.ELEMENTS) }
    // معاينة الوضع الليلي: تغيّر الرسم فقط (ألوان الرموز)، ولا تُخزَّن ولا تُسجَّل في التراجع.
    var darkPreview by rememberSaveable { mutableStateOf(false) }
    val effectiveDesign = remember(project.designSystem, darkPreview) {
        if (darkPreview) com.arabicuieditor.data.model.DarkMode.apply(project.designSystem) else project.designSystem
    }

    val selectedElement = selectedId?.let { ElementTree.find(screen.elements, it) }
    // إطار العنصر المحدد نسبةً إلى أبيه (للوحة الخصائص) وأصل منطقة محتوى أبيه.
    val parentContent = selectedElement?.let { layout.parentContentOf(it.id) }
    val selectedRect = selectedElement?.let { el ->
        layout.frames[el.id]?.let { f ->
            val c = layout.parentContentOf(el.id)
            DesignRect(f.x - c.x, f.y - c.y, f.w, f.h)
        }
    }

    /** مركز المساحة المرئية بإحداثيات التصميم، مع إزاحة متدرجة كي لا تتراكم العناصر في نقطة واحدة. */
    fun newElementCenter(): Pair<Float, Float>? {
        val vpNow = viewport ?: return null
        val cascade = (ElementTree.count(screen.elements) % 6) * CASCADE_STEP_DP
        return (CanvasMath.toDesignX(vpNow, density, canvasSize.width / 2f) + cascade) to
            (CanvasMath.toDesignY(vpNow, density, canvasSize.height / 2f) + cascade)
    }

    LaunchedEffect(screen.id, previewW, previewH, canvasSize) {
        if (viewport == null && canvasSize.width > 0 && canvasSize.height > 0) {
            viewport = CanvasMath.fit(
                viewW = canvasSize.width.toFloat(),
                viewH = canvasSize.height.toFloat(),
                frameW = previewW,
                frameH = previewH,
                density = density,
                marginPx = FIT_MARGIN_DP * density
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootWindowPos = it.positionInWindow() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTabs(
                project = project,
                currentId = screen.id,
                onSelect = viewModel::selectScreen,
                onAdd = viewModel::addScreen
            )
            if (saveFailed) {
                Text(
                    text = stringResource(R.string.editor_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { canvasBounds = it.boundsInWindow() }
            ) {
                val vp = viewport
                if (vp != null) {
                    DesignCanvas(
                        screen = screen,
                        selectedId = selectedId,
                        viewport = vp,
                        onViewportChange = { viewport = it },
                        onSelect = viewModel::selectElement,
                        onElementRectChange = viewModel::setElementRect,
                        onSizeChange = { canvasSize = it },
                        modifier = Modifier.fillMaxSize(),
                        layout = layout,
                        editable = editable,
                        onElementDrop = viewModel::dropElement,
                        onElementRotate = { id, degrees ->
                            viewModel.setProperty(id, com.arabicuieditor.data.model.PropertyKeys.ROTATION, degrees.toString())
                        },
                        onElementRepeat = { viewModel.duplicateSelected() },
                        designSystem = effectiveDesign,
                        snap = SnapSettings(project.snapObjects, project.gridSize)
                    )
                    // شريحة حجم المعاينة: نقرة تنتقل إلى الحجم التالي (حجم التصميم ثم الأحجام الجاهزة).
                    AssistChip(
                        onClick = {
                            val (nw, nh) = PreviewSizes.next(screen.width, screen.height, previewW, previewH)
                            val toBase = nw == screen.width && nh == screen.height
                            viewModel.setPreviewSize(if (toBase) 0f else nw, if (toBase) 0f else nh)
                        },
                        label = {
                            Text(
                                "${previewW.toInt()}×${previewH.toInt()}" +
                                    if (editable) "" else " · " + stringResource(R.string.preview_label)
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .testTag("preview_pill")
                    )
                    androidx.compose.material3.FilterChip(
                        selected = darkPreview,
                        onClick = { darkPreview = !darkPreview },
                        label = {
                            Text(
                                stringResource(if (darkPreview) R.string.mode_dark else R.string.mode_light)
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .testTag("dark_toggle")
                    )
                } else {
                    // نقيس الحجم أولًا ثم نرسم المساحة بعد حساب الملاءمة.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { canvasSize = it.size }
                    )
                }
            }

            HorizontalDivider()
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // تبويبات اللوحة. الضغط على التبويب المفتوح يطويها لتكبير مساحة التصميم.
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BottomTab.entries.forEach { t ->
                            FilterChip(
                                selected = openTab == t,
                                onClick = { openTab = if (openTab == t) null else t },
                                label = { Text(stringResource(t.labelRes), maxLines = 1) },
                                modifier = Modifier.testTag("tab_${t.name}")
                            )
                        }
                    }
                    if (openTab != null) {
                        Spacer(Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(PANEL_HEIGHT_DP.dp)) {
                            CompositionLocalProvider(LocalDesignSystem provides effectiveDesign) {
                            when (openTab) {
                                BottomTab.ELEMENTS -> ElementPalette(
                                    onTapAdd = { type ->
                                        val (cx, cy) = newElementCenter() ?: return@ElementPalette
                                        viewModel.addElement(type, cx, cy)
                                    },
                                    onDragStart = { type, pos -> drag = PaletteDrag(type, pos) },
                                    onDragMove = { pos -> drag = drag?.copy(windowPos = pos) },
                                    onDragEnd = {
                                        val d = drag
                                        val vpNow = viewport
                                        if (d != null && vpNow != null && canvasBounds.contains(d.windowPos)) {
                                            val local = d.windowPos - canvasBounds.topLeft
                                            viewModel.addElement(
                                                d.type,
                                                CanvasMath.toDesignX(vpNow, density, local.x),
                                                CanvasMath.toDesignY(vpNow, density, local.y),
                                                atPoint = true
                                            )
                                        }
                                        drag = null
                                    },
                                    onDragCancel = { drag = null }
                                )

                                BottomTab.LIBRARY -> LibraryPanel(
                                    onAdd = { preset ->
                                        val (cx, cy) = newElementCenter() ?: return@LibraryPanel
                                        viewModel.addPreset(preset, cx, cy)
                                    },
                                    userComponents = userComponents,
                                    canSaveSelected = selectedElement != null,
                                    selectedName = selectedElement?.let { el ->
                                        el.properties[com.arabicuieditor.data.model.PropertyKeys.NAME].orEmpty()
                                    }.orEmpty(),
                                    onSaveSelected = viewModel::saveSelectedToLibrary,
                                    onAddUser = { comp ->
                                        val (cx, cy) = newElementCenter() ?: return@LibraryPanel
                                        viewModel.addUserComponent(comp, cx, cy)
                                    },
                                    onRenameUser = viewModel::renameUserComponent,
                                    onDeleteUser = viewModel::deleteUserComponent,
                                    onUseTemplate = viewModel::addTemplate
                                )

                                BottomTab.PROPERTIES -> PropertiesPanel(
                                    element = selectedElement,
                                    rect = selectedRect,
                                    onSetProperty = { key, value ->
                                        selectedElement?.let { viewModel.setProperty(it.id, key, value) }
                                    },
                                    onSetRect = { r ->
                                        val c = parentContent
                                        if (selectedElement != null && c != null) {
                                            viewModel.setElementRect(
                                                selectedElement.id,
                                                DesignRect(r.x + c.x, r.y + c.y, r.w, r.h)
                                            )
                                        }
                                    },
                                    onDuplicate = viewModel::duplicateSelected,
                                    onDelete = viewModel::deleteSelected,
                                    onReorder = viewModel::reorderSelected,
                                    modifier = Modifier.fillMaxSize(),
                                    parentLayout = selectedElement?.let { layout.parentLayoutOf(it.id) }
                                        ?: ChildLayout.ABSOLUTE,
                                    onSetSizeMode = { axis, mode ->
                                        selectedElement?.let { viewModel.setSizeMode(it.id, axis, mode) }
                                    },
                                    onSetPercent = { axis, percent ->
                                        selectedElement?.let { viewModel.setPercent(it.id, axis, percent) }
                                    },
                                    onSetChildLayout = { l ->
                                        selectedElement?.let { viewModel.setChildLayout(it.id, l) }
                                    },
                                    onWrap = viewModel::wrapSelected,
                                    onUnwrap = viewModel::unwrapSelected,
                                    onAlign = viewModel::alignSelected,
                                    onDistribute = viewModel::distributeSelected,
                                    screens = project.screens,
                                    onSetClick = { action ->
                                        selectedElement?.let { viewModel.setClickAction(it.id, action) }
                                    }
                                )

                                BottomTab.LAYERS -> LayersPanel(
                                    elements = screen.elements,
                                    selectedId = selectedId,
                                    onSelect = { viewModel.selectElement(it) },
                                    modifier = Modifier.fillMaxSize()
                                )

                                BottomTab.SCREEN -> ScreenPanel(
                                    project = project,
                                    screen = screen,
                                    previewWidth = previewW,
                                    previewHeight = previewH,
                                    onSelectScreen = viewModel::selectScreen,
                                    onRename = { viewModel.renameScreen(screen.id, it) },
                                    onDuplicate = { viewModel.duplicateScreen(screen.id) },
                                    onDelete = { viewModel.deleteScreen(screen.id) },
                                    onSetStart = { viewModel.setStartScreen(screen.id) },
                                    onSetPreview = viewModel::setPreviewSize,
                                    onSetScreenSize = { w, h -> viewModel.setScreenSize(screen.id, w, h) },
                                    onSetLayout = viewModel::setScreenLayout,
                                    onSetScreenProperty = viewModel::setScreenProperty,
                                    snapObjects = project.snapObjects,
                                    gridSize = project.gridSize,
                                    onSetSnapObjects = viewModel::setSnapObjects,
                                    onSetGrid = viewModel::setGridSize,
                                    modifier = Modifier.fillMaxSize()
                                )

                                BottomTab.DESIGN -> DesignSystemPanel(
                                    designSystem = project.designSystem,
                                    onThemeSelect = viewModel::applyDesignTheme,
                                    onColor = viewModel::setDesignColor,
                                    onRadius = viewModel::setDesignRadius,
                                    onTextSize = viewModel::setDesignTextSize,
                                    onReset = viewModel::resetDesignSystem,
                                    modifier = Modifier.fillMaxSize()
                                )

                                null -> Unit
                            }
                            }
                        }
                    }
                }
            }
        }

        // عنصر شبحي يتبع الإصبع أثناء السحب من القائمة.
        drag?.let { d ->
            val local = d.windowPos - rootWindowPos
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .offset { IntOffset(local.x.roundToInt() - 40.dp.roundToPx(), local.y.roundToInt() - 56.dp.roundToPx()) }
            ) {
                Text(
                    text = stringResource(elementLabelRes(d.type)),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun ScreenTabs(
    project: Project,
    currentId: String,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        project.screens.forEach { s ->
            FilterChip(
                selected = s.id == currentId,
                onClick = { onSelect(s.id) },
                label = {
                    Text((if (s.id == project.effectiveStartScreenId) "★ " else "") + s.name, maxLines = 1)
                },
                modifier = Modifier.testTag("screen_tab_${s.id}")
            )
        }
        AssistChip(
            onClick = onAdd,
            label = { Text(stringResource(R.string.editor_add_screen)) }
        )
    }
}
