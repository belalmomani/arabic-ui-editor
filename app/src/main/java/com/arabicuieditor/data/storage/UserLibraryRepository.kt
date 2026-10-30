package com.arabicuieditor.data.storage

import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.newId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** مكوّن حفظه المستخدم في مكتبته الشخصية: عنصر واحد بأبنائه، يصلح للإدراج في أي مشروع. */
data class UserComponent(
    val id: String,
    val name: String,
    val savedAt: Long,
    val root: UiElement
)

/**
 * المكتبة الشخصية: ملف JSON واحد مشترك بين كل المشاريع (خارج مجلدات المشاريع).
 * الكتابة ذرية كالمشاريع (ملف مؤقت ثم استبدال مع نسخة .bak)، والقراءة متسامحة:
 * عنصر تالف يُتجاهل ولا يُسقط بقية المكتبة.
 * الألوان المربوطة برموز نظام التصميم تبقى رموزًا، فيتكيّف المكوّن مع مظهر المشروع الذي يُدرَج فيه.
 */
class UserLibraryRepository(private val file: File) {

    @Synchronized
    fun list(): List<UserComponent> {
        for (candidate in listOf(file, File(file.path + ".bak"))) {
            if (!candidate.exists()) continue
            try {
                return decode(candidate.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                // ملف تالف: جرّب النسخة الاحتياطية.
            }
        }
        return emptyList()
    }

    /** يضيف مكوّنًا جديدًا في أول القائمة. يعيد القائمة الجديدة. */
    @Synchronized
    fun add(name: String, root: UiElement): List<UserComponent> {
        val item = UserComponent(newId(), cleanName(name), System.currentTimeMillis(), root)
        return write(listOf(item) + list())
    }

    @Synchronized
    fun rename(id: String, newName: String): List<UserComponent> {
        val clean = cleanName(newName)
        return write(list().map { if (it.id == id) it.copy(name = clean) else it })
    }

    @Synchronized
    fun delete(id: String): List<UserComponent> = write(list().filterNot { it.id == id })

    private fun write(items: List<UserComponent>): List<UserComponent> {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        val backup = File(file.path + ".bak")
        FileOutputStream(tmp).use { out ->
            out.write(encode(items).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (file.exists()) {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return items
    }

    private fun cleanName(raw: String): String {
        val name = raw.trim().take(MAX_NAME_LENGTH)
        require(name.isNotEmpty()) { "Component name must not be blank" }
        return name
    }

    companion object {
        const val MAX_NAME_LENGTH = 40
        private const val VERSION = 1

        internal fun encode(items: List<UserComponent>): String = JSONObject().apply {
            put("version", VERSION)
            put("items", JSONArray().also { arr ->
                items.forEach {
                    arr.put(JSONObject().apply {
                        put("id", it.id)
                        put("name", it.name)
                        put("savedAt", it.savedAt)
                        put("element", ProjectJsonCodec.elementToJson(it.root))
                    })
                }
            })
        }.toString()

        internal fun decode(text: String): List<UserComponent> {
            val arr = JSONObject(text).optJSONArray("items") ?: return emptyList()
            val out = ArrayList<UserComponent>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                try {
                    out += UserComponent(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        savedAt = o.optLong("savedAt", 0L),
                        root = ProjectJsonCodec.elementFromJson(o.getJSONObject("element"))
                    )
                } catch (_: Exception) {
                    // عنصر تالف: يُتجاهل.
                }
            }
            return out
        }
    }
}
