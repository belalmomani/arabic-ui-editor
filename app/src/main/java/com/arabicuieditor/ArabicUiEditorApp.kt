package com.arabicuieditor

import android.app.Application
import com.arabicuieditor.core.AppContainer

/**
 * نقطة الدخول للتطبيق.
 * يحتفظ بـ [AppContainer] الذي ستُسجَّل فيه الخدمات الاختيارية لاحقًا.
 */
class ArabicUiEditorApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
