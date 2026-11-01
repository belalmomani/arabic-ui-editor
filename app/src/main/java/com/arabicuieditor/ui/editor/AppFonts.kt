package com.arabicuieditor.ui.editor

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.arabicuieditor.data.model.FontCatalog

class FontOption(val id: String, val label: String, val family: FontFamily?, val isSystem: Boolean)

/** خطوط النظام + أي ملفات خطوط مخصصة وُضعت في res/font (تُكتشف وقت التشغيل بلا تعديل كود). */
object AppFonts {

    private fun systemLabel(context: Context, id: String): String = context.getString(
        when (id) {
            FontCatalog.SANS -> com.arabicuieditor.R.string.font_sans
            FontCatalog.SERIF -> com.arabicuieditor.R.string.font_serif
            FontCatalog.MONO -> com.arabicuieditor.R.string.font_mono
            FontCatalog.CURSIVE -> com.arabicuieditor.R.string.font_cursive
            else -> com.arabicuieditor.R.string.font_default
        }
    )

    private fun systemFamily(id: String): FontFamily? = when (id) {
        FontCatalog.SANS -> FontFamily.SansSerif
        FontCatalog.SERIF -> FontFamily.Serif
        FontCatalog.MONO -> FontFamily.Monospace
        FontCatalog.CURSIVE -> FontFamily.Cursive
        else -> null
    }

    @Suppress("DiscouragedApi")
    private fun customResId(context: Context, id: String): Int =
        context.resources.getIdentifier(id, "font", context.packageName)

    /** عائلة الخط للمعرّف؛ null يعني الافتراضي (معرّف مجهول أو ملف غير موجود). */
    fun familyFor(context: Context, id: String): FontFamily? {
        if (FontCatalog.isSystem(id)) return systemFamily(id)
        if (!FontCatalog.isValid(id)) return null
        val res = customResId(context, id)
        return if (res == 0) null else try {
            FontFamily(Font(res))
        } catch (_: Exception) {
            null
        }
    }

    fun options(context: Context): List<FontOption> {
        val out = ArrayList<FontOption>()
        FontCatalog.system.forEach { out += FontOption(it, systemLabel(context, it), systemFamily(it), true) }
        FontCatalog.candidates.forEach { c ->
            val family = familyFor(context, c.id)
            if (family != null) out += FontOption(c.id, c.label, family, false)
        }
        return out
    }
}

@Composable
fun rememberFontFamily(id: String): FontFamily? {
    val context = LocalContext.current
    return remember(id) { AppFonts.familyFor(context, id) }
}
