package com.arabicuieditor.data.storage

import com.arabicuieditor.data.model.CURRENT_SCHEMA_VERSION
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.newId
import com.arabicuieditor.data.model.newProject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * تخزين المشاريع محليًا. لكل مشروع مجلد خاص:
 *
 * ```
 * <root>/<projectId>/project.json      بيانات التصميم (JSON)
 * <root>/<projectId>/project.json.bak  آخر نسخة سليمة سابقة
 * <root>/<projectId>/resources/        الصور والموارد (ملفات منفصلة)
 * ```
 *
 * الكتابة ذرية: يُكتب ملف مؤقت ثم يُستبدل به الملف الأصلي، فلا يبقى ملف نصف مكتوب
 * إذا أُغلق التطبيق فجأة. عند القراءة نجرّب الأصلي ثم النسخة الاحتياطية ثم المؤقت.
 */
class ProjectRepository(private val root: File) {

    init {
        root.mkdirs()
    }

    @Synchronized
    fun create(name: String, firstScreenName: String): Project {
        val project = newProject(cleanName(name), firstScreenName)
        write(project)
        return project
    }

    /** كل المشاريع السليمة، الأحدث تعديلًا أولًا. الملفات التالفة كليًا تُتجاهل. */
    @Synchronized
    fun list(): List<Project> =
        root.listFiles { f -> f.isDirectory }.orEmpty()
            .mapNotNull { load(it.name) }
            .sortedByDescending { it.updatedAt }

    @Synchronized
    fun load(id: String): Project? {
        if (!isValidId(id)) return null
        val main = projectFile(id)
        val candidates = listOf(main, File(main.path + ".bak"), File(main.path + ".tmp"))
        for (file in candidates) {
            if (!file.exists()) continue
            try {
                return ProjectJsonCodec.decode(file.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                // ملف تالف أو من مخطط أحدث: جرّب المرشح التالي.
            }
        }
        return null
    }

    /** يحفظ المشروع ويحدّث وقت آخر تعديل. يعيد النسخة المحفوظة. */
    @Synchronized
    fun save(project: Project): Project {
        val saved = project.copy(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            updatedAt = System.currentTimeMillis()
        )
        write(saved)
        return saved
    }

    @Synchronized
    fun rename(id: String, newName: String): Project? {
        val project = load(id) ?: return null
        return save(project.copy(name = cleanName(newName)))
    }

    /** ينسخ المشروع كاملًا بما فيه مجلد الموارد، بمعرّف جديد. */
    @Synchronized
    fun duplicate(id: String, newName: String): Project? {
        val source = load(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = source.copy(id = newId(), name = cleanName(newName), createdAt = now, updatedAt = now)
        val sourceResources = resourcesDir(id)
        if (sourceResources.exists()) {
            sourceResources.copyRecursively(resourcesDir(copy.id), overwrite = true)
        }
        write(copy)
        return copy
    }

    @Synchronized
    fun delete(id: String): Boolean {
        if (!isValidId(id)) return false
        return projectDir(id).deleteRecursively()
    }

    fun resourcesDir(id: String): File = File(projectDir(id), "resources")

    // ---- داخلي ----

    private fun projectDir(id: String) = File(root, id)

    private fun projectFile(id: String) = File(projectDir(id), "project.json")

    private fun write(project: Project) {
        require(isValidId(project.id)) { "Invalid project id" }
        val dir = projectDir(project.id)
        dir.mkdirs()
        resourcesDir(project.id).mkdirs()

        val target = projectFile(project.id)
        val tmp = File(target.path + ".tmp")
        val backup = File(target.path + ".bak")

        FileOutputStream(tmp).use { out ->
            out.write(ProjectJsonCodec.encode(project).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (target.exists()) {
            target.copyTo(backup, overwrite = true)
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun cleanName(raw: String): String {
        val name = raw.trim().take(MAX_NAME_LENGTH)
        require(name.isNotEmpty()) { "Project name must not be blank" }
        return name
    }

    private fun isValidId(id: String) = ID_REGEX.matches(id)

    companion object {
        const val MAX_NAME_LENGTH = 60
        private val ID_REGEX = Regex("^[A-Za-z0-9-]{8,64}$")
    }
}
