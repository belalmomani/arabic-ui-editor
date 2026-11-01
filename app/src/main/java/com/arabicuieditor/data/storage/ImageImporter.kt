package com.arabicuieditor.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.arabicuieditor.data.model.ImageNames
import java.io.File

/** ينسخ صورة من المعرض إلى مجلد موارد المشروع مصغّرة (أطول ضلع ≤ 1600). يعيد اسم الملف أو null. */
object ImageImporter {
    fun import(context: Context, uri: Uri, dir: File): String? = try {
        dir.mkdirs()
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = ImageNames.sampleSize(bounds.outWidth, bounds.outHeight, ImageNames.MAX_SIDE_PX)
        }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp == null) null else {
            val png = bmp.hasAlpha()
            val name = ImageNames.nextName(dir.list().orEmpty().toSet(), if (png) "png" else "jpg")
            val tmp = File(dir, "$name.tmp")
            tmp.outputStream().use {
                bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it)
            }
            if (!tmp.renameTo(File(dir, name))) { tmp.delete(); null } else name
        }
    } catch (_: Throwable) {
        null
    }
}
