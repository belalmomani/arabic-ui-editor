package com.arabicuieditor.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.OutputStream

/**
 * ربط مجلد Google Drive (أو أي مجلد سحابي/محلي) عبر Storage Access Framework:
 * يختار المستخدم المجلد مرة واحدة من تطبيق Drive، ويُحفظ الإذن، ثم يُرفع كل مشروع كملف ZIP
 * (يُستبدل الملف إن وُجد بالاسم نفسه). لا حاجة لمفاتيح Google Cloud ولا تسجيل دخول داخل التطبيق.
 */
object DriveSync {
    private const val PREFS = "drive_sync"
    private const val KEY_TREE = "tree_uri"
    const val MIME_ZIP = "application/zip"

    /** اسم ملف آمن للمشروع (حروف/أرقام/شرطات فقط) مع لاحقة zip. */
    fun fileName(projectName: String): String {
        val base = projectName.replace(Regex("[^\\p{L}\\p{N}_ -]"), "_").trim().take(60).ifBlank { "project" }
        return "$base.zip"
    }

    fun treeUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TREE, null)?.let { Uri.parse(it) }

    fun isLinked(context: Context): Boolean = treeUri(context) != null

    /** يحفظ المجلد المختار ويثبّت إذن القراءة والكتابة عليه. */
    fun link(context: Context, tree: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TREE, tree.toString()).apply()
        true
    } catch (_: Exception) {
        false
    }

    fun unlink(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TREE).apply()
    }

    /** الرفع التلقائي: يُرفع كل مشروع تغيّر منذ آخر رفع عند فتح شاشة المشاريع. */
    fun autoEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto", false)

    fun setAuto(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("auto", on).apply()
    }

    fun lastUploaded(context: Context, id: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("up_$id", 0L)

    fun markUploaded(context: Context, id: String, at: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("up_$id", at).apply()
    }

    /** هل يحتاج المشروع رفعًا؟ (تعديله أحدث من آخر رفع). منطق نقي. */
    fun needsUpload(updatedAt: Long, lastUploaded: Long) = updatedAt > lastUploaded

    /** يكتب ملفًا باسم [name] داخل المجلد المربوط عبر [writer]؛ يستبدل الموجود. false عند أي فشل. */
    fun upload(context: Context, name: String, writer: (OutputStream) -> Boolean): Boolean {
        val tree = treeUri(context) ?: return false
        return try {
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val existing = findChild(context, tree, name)
            val target = existing ?: DocumentsContract.createDocument(resolver, parent, MIME_ZIP, name) ?: return false
            resolver.openOutputStream(target, "wt")?.use { writer(it) } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** ملفات ZIP في المجلد المربوط: (الاسم، المسار) مرتبة بالاسم؛ فارغة عند أي فشل. */
    fun listZips(context: Context): List<Pair<String, Uri>> {
        val tree = treeUri(context) ?: return emptyList()
        return try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val out = ArrayList<Pair<String, Uri>>()
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (name.endsWith(".zip", ignoreCase = true)) {
                        out += name to DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    }
                }
            }
            out.sortedBy { it.first }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findChild(context: Context, tree: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        return null
    }
}
