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

    /** رسالة نجاح عابرة (مورد string) أو null. */
    private val _info = MutableStateFlow<Int?>(null)
    val info: StateFlow<Int?> = _info.asStateFlow()
    fun clearInfo() { _info.value = null }

    private val _driveLinked = MutableStateFlow(
        com.arabicuieditor.data.storage.DriveSync.isLinked(app)
    )
    val driveLinked: StateFlow<Boolean> = _driveLinked.asStateFlow()

    /** يحفظ مجلد Drive المختار؛ إن نجح يُرفع [thenUpload] (مشروع) فورًا. */
    fun linkDrive(tree: android.net.Uri, thenUpload: String?) {
        val ok = com.arabicuieditor.data.storage.DriveSync.link(getApplication(), tree)
        _driveLinked.value = ok
        if (!ok) { _error.value = R.string.drive_link_failed; return }
        _info.value = R.string.drive_linked
        if (thenUpload != null) uploadToDrive(thenUpload)
    }

    fun unlinkDrive() {
        com.arabicuieditor.data.storage.DriveSync.unlink(getApplication())
        _driveLinked.value = false
        _info.value = R.string.drive_unlinked
    }

    private val _driveFiles = MutableStateFlow<List<Pair<String, android.net.Uri>>?>(null)
    /** قائمة ZIP في مجلد Drive المربوط حين يُفتح حوار الاستعادة، وإلا null. */
    val driveFiles: StateFlow<List<Pair<String, android.net.Uri>>?> = _driveFiles.asStateFlow()

    fun openDriveList() {
        viewModelScope.launch {
            _driveFiles.value = withContext(Dispatchers.IO) {
                com.arabicuieditor.data.storage.DriveSync.listZips(getApplication())
            }
        }
    }

    fun closeDriveList() { _driveFiles.value = null }

    fun importFromDrive(uri: android.net.Uri) {
        _driveFiles.value = null
        viewModelScope.launch {
            val imported = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { repository.importZip(it) }
                }
            } catch (e: Exception) {
                null
            }
            if (imported == null) _error.value = R.string.drive_import_failed else _info.value = R.string.drive_import_ok
            reload()
        }
    }

    fun uploadToDrive(id: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) {
                val project = repository.load(id) ?: return@withContext false
                com.arabicuieditor.data.storage.DriveSync.upload(
                    app, com.arabicuieditor.data.storage.DriveSync.fileName(project.name)
                ) { out -> repository.exportZip(id, out) }
            }
            if (ok) {
                repository.load(id)?.let { com.arabicuieditor.data.storage.DriveSync.markUploaded(app, id, it.updatedAt) }
                _info.value = R.string.drive_upload_ok
            } else _error.value = R.string.drive_upload_failed
        }
    }

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

    /** يستورد مشروعًا من نص نسخة احتياطية؛ عند الفشل يظهر خطأ. */
    fun importText(text: String) {
        viewModelScope.launch {
            val imported = try {
                withContext(Dispatchers.IO) { repository.importJson(text) }
            } catch (e: Exception) {
                null
            }
            if (imported == null) _error.value = R.string.import_failed
            reload()
        }
    }

    /** يبدّل نصوص مشروع موجود بردّ الذكاء الاصطناعي؛ [onDone] بعدد النصوص المبدَّلة (0 = لم يصلح الرد). */
    fun applyAiContent(projectId: String, reply: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val n = try {
                withContext(Dispatchers.IO) {
                    val project = repository.load(projectId)
                    val r = project?.let { com.arabicuieditor.data.ai.AiContent.apply(it, reply) }
                    if (r != null) { repository.save(r.first); r.second } else 0
                }
            } catch (e: Exception) { 0 }
            reload()
            onDone(n)
        }
    }

    /** ينشئ مشروعًا من «مخطِّط التطبيق» (بلا ذكاء اصطناعي) ويفتحه. */
    fun createPlanned(plan: com.arabicuieditor.data.planner.AppPlan, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val project = withContext(Dispatchers.IO) {
                    repository.save(com.arabicuieditor.data.planner.AppPlanner.build(plan, { app.getString(it) }))
                }
                reload()
                onCreated(project.id)
            } catch (e: Exception) {
                _error.value = R.string.error_action_failed
            }
        }
    }

    /** ينشئ مشروعًا من ردّ الذكاء الاصطناعي الملصوق؛ يعيد النجاح عبر [onDone]. */
    fun importAi(reply: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) { repository.importAi(reply) } != null
            } catch (e: Exception) {
                false
            }
            reload()
            onDone(ok)
        }
    }

    /** يكتب نسخة ZIP كاملة للمشروع إلى [uri] (يختاره المستخدم). */
    fun exportZip(id: String, uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { repository.exportZip(id, it) } ?: false
                }
            } catch (e: Exception) {
                false
            }
            if (!ok) _error.value = R.string.error_action_failed
        }
    }

    /** يكتب «مخطط التطبيق» (Markdown) إلى [uri]. */
    fun exportBlueprint(id: String, uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    val project = repository.load(id) ?: return@withContext false
                    val md = com.arabicuieditor.data.planner.Blueprint.markdown(project)
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(md.toByteArray(Charsets.UTF_8)) } != null
                }
            } catch (e: Exception) {
                false
            }
            if (!ok) _error.value = R.string.error_action_failed
        }
    }

    fun importZip(uri: android.net.Uri) {
        viewModelScope.launch {
            val imported = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { repository.importZip(it) }
                }
            } catch (e: Exception) {
                null
            }
            if (imported == null) _error.value = R.string.import_zip_failed
            reload()
        }
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
        autoUpload()
    }

    private val _driveAuto = MutableStateFlow(com.arabicuieditor.data.storage.DriveSync.autoEnabled(app))
    val driveAuto: StateFlow<Boolean> = _driveAuto.asStateFlow()

    fun toggleDriveAuto() {
        val on = !_driveAuto.value
        com.arabicuieditor.data.storage.DriveSync.setAuto(getApplication(), on)
        _driveAuto.value = on
        _info.value = if (on) R.string.drive_auto_on else R.string.drive_auto_off
        if (on) viewModelScope.launch { autoUpload() }
    }

    private var autoRunning = false

    /** يرفع المشاريع المعدَّلة منذ آخر رفع إن كان الرفع التلقائي مفعّلًا والمجلد مربوطًا. */
    private suspend fun autoUpload() {
        val app = getApplication<Application>()
        val ds = com.arabicuieditor.data.storage.DriveSync
        if (autoRunning || !_driveAuto.value || !ds.isLinked(app)) return
        autoRunning = true
        try {
            var done = 0
            var failed = false
            for (p in _projects.value) {
                if (!ds.needsUpload(p.updatedAt, ds.lastUploaded(app, p.id))) continue
                val ok = withContext(Dispatchers.IO) {
                    ds.upload(app, ds.fileName(p.name)) { out -> repository.exportZip(p.id, out) }
                }
                if (ok) { ds.markUploaded(app, p.id, p.updatedAt); done++ } else { failed = true; break }
            }
            if (failed) _error.value = R.string.drive_upload_failed
            else if (done > 0) _info.value = R.string.drive_auto_done
        } finally {
            autoRunning = false
        }
    }
}
