package com.arabicuieditor.ui.editor.canvas

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.arabicuieditor.data.model.ImageNames
import com.arabicuieditor.data.model.Stickers
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** مجلد موارد المشروع الحالي (null خارج المحرر، فتظهر الصورة مربعًا رماديًا). */
val LocalImageDir = compositionLocalOf<File?> { null }

/** يحمّل صورة المشروع مصغّرة في الخلفية. يعيد null أثناء التحميل أو إن فُقد الملف. */
@Composable
fun rememberProjectImage(name: String): ImageBitmap? {
    if (name.isNotEmpty() && ImageNames.isValid(name) && Stickers.isSticker(name)) return rememberStickerImage(name)
    val dir = LocalImageDir.current
    if (dir == null || name.isEmpty() || !ImageNames.isValid(name)) return null
    val file = File(dir, name)
    val stamp = file.lastModified()
    val bitmap by produceState<ImageBitmap?>(null, file.path, stamp) {
        value = withContext(Dispatchers.IO) { decode(file) }
    }
    return bitmap
}

private fun decode(file: File): ImageBitmap? = try {
    if (!file.exists()) null else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = ImageNames.sampleSize(bounds.outWidth, bounds.outHeight, 1024)
        }
        BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()
    }
} catch (_: Throwable) {
    null
}

/** ملصق مضمّن من assets/stickers (لا يعتمد على مجلد المشروع). */
@Composable
private fun rememberStickerImage(name: String): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { StickerCache.load(context, name) }
    }
    return bitmap
}

private object StickerCache {
    private val cache = android.util.LruCache<String, ImageBitmap>(64)

    fun load(context: Context, name: String): ImageBitmap? {
        cache.get(name)?.let { return it }
        val bmp = try {
            context.assets.open(Stickers.assetPath(name)).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
        if (bmp != null) cache.put(name, bmp)
        return bmp
    }
}
