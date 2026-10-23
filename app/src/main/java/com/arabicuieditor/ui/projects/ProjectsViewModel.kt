package com.arabicuieditor.ui.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arabicuieditor.ArabicUiEditorApp
import com.arabicuieditor.R
import com.arabicuieditor.data.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as ArabicUiEditorApp).container.projects

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** معرّف نص الخطأ الأخير (مورد string) أو null. */
    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    fun create(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val firstScreen = getApplication<Application>().getString(R.string.default_screen_name)
            try {
                val project = withContext(Dispatchers.IO) { repository.create(name, firstScreen) }
                onCreated(project.id)
            } catch (e: Exception) {
                _error.value = R.string.error_action_failed
            }
        }
    }

    fun rename(id: String, name: String) = runAndReload { repository.rename(id, name) }

    fun duplicate(project: Project) {
        val copyName = getApplication<Application>().getString(R.string.project_copy_name, project.name)
        runAndReload { repository.duplicate(project.id, copyName) }
    }

    fun delete(id: String) = runAndReload { repository.delete(id) }

    private fun runAndReload(block: () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                _error.value = R.string.error_action_failed
            }
            reload()
        }
    }

    private suspend fun reload() {
        try {
            _projects.value = withContext(Dispatchers.IO) { repository.list() }
        } catch (e: Exception) {
            _error.value = R.string.error_action_failed
        }
        _loaded.value = true
    }
}
