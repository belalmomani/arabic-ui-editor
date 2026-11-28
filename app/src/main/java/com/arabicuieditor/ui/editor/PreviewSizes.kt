package com.arabicuieditor.ui.editor

import androidx.annotation.StringRes
import com.arabicuieditor.R
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.UiScreen

/** حجم معاينة جاهز بوحدة dp. */
data class PreviewSize(val id: String, @StringRes val labelRes: Int, val width: Float, val height: Float)

/**
 * أحجام المعاينة الجاهزة. المعاينة لا تغيّر التصميم المحفوظ: تُعيد حساب التخطيط
 * (الصفوف والأعمدة والأبعاد النسبية وسلوك التثبيت) كما لو كانت الشاشة بهذا الحجم.
 */
object PreviewSizes {
    val presets: List<PreviewSize> = listOf(
        PreviewSize("small", R.string.preview_small, 320f, 568f),
        PreviewSize("standard", R.string.preview_standard, 360f, 640f),
        PreviewSize("large", R.string.preview_large, 412f, 892f),
        PreviewSize("phone_landscape", R.string.preview_phone_landscape, 640f, 360f),
        PreviewSize("tablet_portrait", R.string.preview_tablet_portrait, 600f, 960f),
        PreviewSize("tablet_landscape", R.string.preview_tablet_landscape, 960f, 600f)
    )

    /** أحجام نوافذ Windows الشائعة بوحدة dp (مشاريع Windows فقط). */
    val windowsPresets: List<PreviewSize> = listOf(
        PreviewSize("win_compact", R.string.preview_win_compact, 800f, 600f),
        PreviewSize("win_hd", R.string.preview_win_hd, 1280f, 720f),
        PreviewSize("win_laptop", R.string.preview_win_laptop, 1366f, 768f),
        PreviewSize("win_fhd", R.string.preview_win_fhd, 1920f, 1080f),
        PreviewSize("win_tall", R.string.preview_win_tall, 1024f, 768f)
    )

    fun forPlatform(platform: String): List<PreviewSize> =
        if (platform == com.arabicuieditor.data.model.Platforms.WINDOWS) windowsPresets else presets

    /**
     * التالي في دورة الأحجام: حجم التصميم أولًا ثم الأحجام الجاهزة (دون المطابق لحجم التصميم).
     * من حجم غير موجود في الدورة يعود إلى حجم التصميم.
     */
    fun next(baseW: Float, baseH: Float, curW: Float, curH: Float, list: List<PreviewSize> = presets): Pair<Float, Float> {
        val cycle = listOf(baseW to baseH) +
            list.filterNot { it.width == baseW && it.height == baseH }.map { it.width to it.height }
        val index = cycle.indexOfFirst { it.first == curW && it.second == curH }
        return if (index < 0) cycle.first() else cycle[(index + 1) % cycle.size]
    }
}

/** حجم المعاينة الفعلي للشاشة: المحفوظ في المشروع إن وُجد، وإلا حجم تصميم الشاشة. */
fun Project.previewSizeFor(screen: UiScreen): Pair<Float, Float> =
    if (previewWidth > 0f && previewHeight > 0f) previewWidth to previewHeight else screen.width to screen.height
