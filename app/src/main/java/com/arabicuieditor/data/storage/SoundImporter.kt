package com.arabicuieditor.data.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arabicuieditor.data.model.Sounds
import java.io.File

/** يستورد ملف صوت من الجهاز إلى مجلد موارد المشروع باسم usr_N.ext. يعيد الاسم أو null عند الفشل. */
object SoundImporter {
    fun import(context: Context, uri: Uri, dir: File): String? = try {
        val resolver = context.contentResolver
        val display = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        val ext = Sounds.customExt(resolver.getType(uri), display)
        val bytes = if (ext == null) null else resolver.openInputStream(uri)?.use { s ->
            val buf = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var ok = true
            while (true) {
                val n = s.read(chunk)
                if (n < 0) break
                buf.write(chunk, 0, n)
                if (buf.size() > Sounds.MAX_CUSTOM_BYTES) { ok = false; break }
            }
            if (ok && buf.size() > 0) buf.toByteArray() else null
        }
        if (ext == null || bytes == null) null
        else {
            dir.mkdirs()
            val name = Sounds.nextCustomName(dir.list().orEmpty().toSet(), ext)
            File(dir, name).writeBytes(bytes)
            name
        }
    } catch (_: Exception) {
        null
    }
}
