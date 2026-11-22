package com.arabicuieditor.ui.projects

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.arabicuieditor.ArabicUiEditorApp
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.planner.ContentSlot
import com.arabicuieditor.data.planner.ContentSlots
import com.arabicuieditor.data.planner.SetupStore
import com.arabicuieditor.data.storage.ImageImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** مرحلة «تكملة بناء التطبيق»: كل حفظ يُكتب فورًا في المشروع نفسه (المعرّف نفسه). */
class FillViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    private val repository = (app as ArabicUiEditorApp).container.projects
    val projectId: String = saved.get<String>("projectId").orEmpty()

    private val _slots = MutableStateFlow<List<ContentSlot>>(emptyList())
    val slots: StateFlow<List<ContentSlot>> = _slots.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    /** معرّفات الصور المضافة في هذه الجلسة (لإظهار علامة تمّ). */
    private val _doneImages = MutableStateFlow<Set<String>>(emptySet())
    val doneImages: StateFlow<Set<String>> = _doneImages.asStateFlow()
    private val _imageError = MutableStateFlow(false)
    val imageError: StateFlow<Boolean> = _imageError.asStateFlow()

    init {
        viewModelScope.launch {
            val p = withContext(Dispatchers.IO) { repository.load(projectId) }
            _slots.value = p?.let { ContentSlots.extract(it) }.orEmpty()
            _loaded.value = true
        }
    }

    /** يطبّق قيم مرحلة (الفارغ يُتجاهل) ويحفظ فورًا. */
    fun save(values: Map<String, String>) {
        if (values.values.all { it.isBlank() }) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val p: Project = repository.load(projectId) ?: return@withContext
                val q = ContentSlots.apply(p, values)
                if (q != p) repository.save(q)
            }
        }
    }

    fun importImage(elementId: String, uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val name = ImageImporter.import(getApplication(), uri, repository.resourcesDir(projectId)) ?: return@withContext false
                val p = repository.load(projectId) ?: return@withContext false
                val q = ContentSlots.apply(p, mapOf(elementId to name))
                if (q != p) repository.save(q)
                q != p
            }
            if (ok) _doneImages.value = _doneImages.value + elementId else _imageError.value = true
        }
    }

    fun clearImageError() { _imageError.value = false }

    /** ينهي الإعداد فلا يظهر الزر النابض بعد الآن. */
    fun finish() { SetupStore.remove(getApplication(), projectId) }
}
