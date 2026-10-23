package com.arabicuieditor.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.arabicuieditor.ArabicUiEditorApp
import com.arabicuieditor.R
import com.arabicuieditor.data.model.ComponentPreset
import com.arabicuieditor.data.model.ElementOps
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.newElement
import com.arabicuieditor.data.model.newId
import com.arabicuieditor.data.model.withProp
import com.arabicuieditor.ui.editor.canvas.DesignRect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface EditorState {
    data object Loading : EditorState
    data object NotFound : EditorState
    data class Ready(val project: Project) : EditorState
}

enum class ReorderOp { FORWARD, BACKWARD, TO_FRONT, TO_BACK }

/**
 * يحمّل مشروعًا ويطبّق عليه التعديلات مع حفظ تلقائي وتراجع/إعادة:
 * - حفظ مؤجل بعد 400ms من آخر تعديل (يجمع التعديلات المتتالية، مثل السحب).
 * - حفظ فوري عند إيقاف الشاشة أو الخروج منها.
 * - كل تعديل يمر عبر [edit] الذي يسجّل لقطة في [EditHistory] (تتضمن تعديل الخصائص والتحريك
 *   والحذف والترتيب والإضافة). التراجع يستعيد قائمة الشاشات ثم يُحفظ كأي تعديل.
 *
 * حالة الواجهة المؤقتة (الشاشة الحالية، العنصر المحدد) هنا أيضًا ولا تُحفظ في الملف.
 * سجل التراجع يبقى في الذاكرة فقط: يُفقد عند إغلاق المحرر، لكن التصميم نفسه محفوظ.
 */
class EditorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val repository = (app as ArabicUiEditorApp).container.projects
    private val projectId: String = handle.get<String>("projectId").orEmpty()

    private val _state = MutableStateFlow<EditorState>(EditorState.Loading)
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    private val _currentScreenId = MutableStateFlow<String?>(null)
    val currentScreenId: StateFlow<String?> = _currentScreenId.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val history = EditHistory<List<UiScreen>>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var dirty = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val project = try {
                withContext(Dispatchers.IO) { repository.load(projectId) }
            } catch (e: Exception) {
                null
            }
            if (project == null) {
                _state.value = EditorState.NotFound
            } else {
                _currentScreenId.value = project.screens.firstOrNull()?.id
                _state.value = EditorState.Ready(project)
            }
        }
    }

    private fun str(id: Int): String = getApplication<Application>().getString(id)

    // ---- التحديد والتنقل ----

    fun selectScreen(id: String) {
        _currentScreenId.value = id
        _selectedId.value = null
    }

    fun selectElement(id: String?) {
        _selectedId.value = id
    }

    fun addScreen() {
        val project = (_state.value as? EditorState.Ready)?.project ?: return
        val name = getApplication<Application>()
            .getString(R.string.editor_new_screen_name, project.screens.size + 1)
        val screen = UiScreen(id = newId(), name = name)
        edit { it.copy(screens = it.screens + screen) }
        _currentScreenId.value = screen.id
        _selectedId.value = null
    }

    // ---- تعديل التصميم ----

    /** يضيف عنصرًا مركزه (centerX, centerY) بإحداثيات التصميم ويحدده. */
    fun addElement(type: String, centerX: Float, centerY: Float) {
        val label = str(elementLabelRes(type))
        insertElements(listOf(newElement(type, 0f, 0f, label)), centerX, centerY)
    }

    /** يضيف مكوّنًا جاهزًا من المكتبة (عنصر أو عدة عناصر) ويحدد أول عنصر فيه. */
    fun addPreset(preset: ComponentPreset, centerX: Float, centerY: Float) {
        val built = preset.build { str(it) }
        insertElements(built, centerX, centerY)
    }

    private fun insertElements(raw: List<UiElement>, centerX: Float, centerY: Float) {
        val screen = currentScreen() ?: return
        val placed = ElementOps.placeCentered(raw, centerX, centerY, screen.width, screen.height)
        val named = ElementOps.withUniqueNames(screen.elements, placed) { e -> str(elementLabelRes(e.type)) }
        editScreen(screen.id) { it.copy(elements = it.elements + named) }
        _selectedId.value = named.firstOrNull()?.id
    }

    /** يضبط موضع وحجم عنصر (يحوّل الأبعاد إلى FIXED لأن المحرر يحدد حجمًا ثابتًا). */
    fun setElementRect(elementId: String, rect: DesignRect) {
        val screen = currentScreen() ?: return
        editScreen(screen.id, key = "rect:$elementId") { s ->
            s.copy(elements = s.elements.map { e ->
                if (e.id != elementId) e else e.copy(
                    layout = e.layout.copy(
                        x = rect.x,
                        y = rect.y,
                        width = SizeSpec.fixed(rect.w),
                        height = SizeSpec.fixed(rect.h)
                    )
                )
            })
        }
    }

    /**
     * يضبط خاصية عنصر بعد التحقق منها عبر [PropertyRegistry]. القيم غير الصالحة تُتجاهل.
     * الاسم الداخلي الفارغ مسموح؛ تعرض الواجهة حينها اسم نوع العنصر.
     */
    fun setProperty(elementId: String, key: String, value: String) {
        val screen = currentScreen() ?: return
        val normalized = PropertyRegistry.normalize(key, value) ?: return
        editScreen(screen.id, key = "prop:$elementId:$key") { s ->
            s.copy(elements = s.elements.map { e ->
                if (e.id != elementId) e else e.withProp(key, normalized)
            })
        }
    }

    fun deleteSelected() {
        val id = _selectedId.value ?: return
        val screen = currentScreen() ?: return
        editScreen(screen.id) { s -> s.copy(elements = s.elements.filterNot { it.id == id }) }
        _selectedId.value = null
    }

    fun duplicateSelected() {
        val id = _selectedId.value ?: return
        val screen = currentScreen() ?: return
        val (list, copyId) = ElementOps.duplicate(screen.elements, id, DUPLICATE_OFFSET, str(R.string.copy_suffix)) ?: return
        editScreen(screen.id) { it.copy(elements = list) }
        _selectedId.value = copyId
    }

    fun reorderSelected(op: ReorderOp) {
        val id = _selectedId.value ?: return
        val screen = currentScreen() ?: return
        editScreen(screen.id) { s ->
            s.copy(
                elements = when (op) {
                    ReorderOp.FORWARD -> ElementOps.moveForward(s.elements, id)
                    ReorderOp.BACKWARD -> ElementOps.moveBackward(s.elements, id)
                    ReorderOp.TO_FRONT -> ElementOps.bringToFront(s.elements, id)
                    ReorderOp.TO_BACK -> ElementOps.sendToBack(s.elements, id)
                }
            )
        }
    }

    private fun currentScreen(): UiScreen? {
        val project = (_state.value as? EditorState.Ready)?.project ?: return null
        return project.screens.firstOrNull { it.id == _currentScreenId.value }
            ?: project.screens.firstOrNull()
    }

    private fun editScreen(screenId: String, key: String? = null, transform: (UiScreen) -> UiScreen) {
        edit(key) { p -> p.copy(screens = p.screens.map { if (it.id == screenId) transform(it) else it }) }
    }

    // ---- التراجع والإعادة ----

    fun undo() {
        val current = (_state.value as? EditorState.Ready)?.project ?: return
        val previous = history.undo(current.screens) ?: return
        applyScreens(current, previous)
    }

    fun redo() {
        val current = (_state.value as? EditorState.Ready)?.project ?: return
        val next = history.redo(current.screens) ?: return
        applyScreens(current, next)
    }

    private fun applyScreens(current: Project, screens: List<UiScreen>) {
        commit(current.copy(screens = screens))
        // لا نترك تحديدًا أو شاشة حالية تشير إلى شيء اختفى بعد التراجع.
        if (screens.none { it.id == _currentScreenId.value }) {
            _currentScreenId.value = screens.firstOrNull()?.id
        }
        val visible = screens.firstOrNull { it.id == _currentScreenId.value }
        if (visible == null || visible.elements.none { it.id == _selectedId.value }) {
            _selectedId.value = null
        }
        refreshHistoryFlags()
    }

    private fun refreshHistoryFlags() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    // ---- الحفظ التلقائي ----

    /**
     * يطبّق تعديلًا على المشروع ويسجّله في سجل التراجع ثم يجدول حفظه.
     * [key] يدمج التعديلات المتتالية المتشابهة في خطوة تراجع واحدة. التعديل الذي لا يغيّر شيئًا يُتجاهل.
     */
    fun edit(key: String? = null, transform: (Project) -> Project) {
        val current = _state.value as? EditorState.Ready ?: return
        val next = transform(current.project)
        if (next == current.project) return
        if (next.screens != current.project.screens) {
            history.record(current.project.screens, key)
            refreshHistoryFlags()
        }
        commit(next)
    }

    private fun commit(project: Project) {
        _state.value = EditorState.Ready(project)
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveNow()
        }
    }

    /** يحفظ فورًا إن وُجدت تعديلات غير محفوظة. */
    fun flush() {
        saveJob?.cancel()
        if (dirty) {
            viewModelScope.launch { saveNow() }
        }
    }

    private suspend fun saveNow() {
        val snapshot = (_state.value as? EditorState.Ready)?.project ?: return
        dirty = false
        try {
            val saved = withContext(Dispatchers.IO) { repository.save(snapshot) }
            _saveFailed.value = false
            // نحدّث وقت التعديل فقط كي لا نمسح تعديلات حدثت أثناء الحفظ.
            _state.update { s ->
                if (s is EditorState.Ready) EditorState.Ready(s.project.copy(updatedAt = saved.updatedAt)) else s
            }
        } catch (e: CancellationException) {
            // أُلغي الحفظ بسبب تعديل أحدث؛ الحفظ التالي سيشمله.
            dirty = true
            throw e
        } catch (e: Exception) {
            dirty = true
            _saveFailed.value = true
        }
    }

    override fun onCleared() {
        // آخر فرصة: حفظ متزامن لأن viewModelScope يُلغى الآن.
        if (dirty) {
            val project = (_state.value as? EditorState.Ready)?.project
            if (project != null) {
                try {
                    repository.save(project)
                } catch (_: Exception) {
                }
            }
        }
        super.onCleared()
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 400L
        const val DUPLICATE_OFFSET = 12f
    }
}
