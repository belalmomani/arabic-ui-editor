package com.arabicuieditor.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.arabicuieditor.ArabicUiEditorApp
import com.arabicuieditor.data.model.Project
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

/**
 * يحمّل مشروعًا ويطبّق عليه التعديلات مع حفظ تلقائي:
 * - حفظ مؤجل بعد 400ms من آخر تعديل (يجمع التعديلات المتتالية).
 * - حفظ فوري عند إيقاف الشاشة أو الخروج منها.
 * فإذا أُغلق التطبيق فجأة ضاعت تعديلات آخر ثوانٍ قليلة على الأكثر، ولا يتلف الملف.
 */
class EditorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val repository = (app as ArabicUiEditorApp).container.projects
    private val projectId: String = handle.get<String>("projectId").orEmpty()

    private val _state = MutableStateFlow<EditorState>(EditorState.Loading)
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    private var dirty = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val project = try {
                withContext(Dispatchers.IO) { repository.load(projectId) }
            } catch (e: Exception) {
                null
            }
            _state.value = if (project == null) EditorState.NotFound else EditorState.Ready(project)
        }
    }

    /** يطبّق تعديلًا على المشروع ويجدول حفظه. */
    fun edit(transform: (Project) -> Project) {
        val current = _state.value as? EditorState.Ready ?: return
        _state.value = EditorState.Ready(transform(current.project))
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
    }
}
