package com.arabicuieditor.data.ai

import android.content.Context

/** يحفظ إعداد الذكاء الاصطناعي الاختياري في مساحة التطبيق الخاصة (لا يدخل في المشاريع ولا في التصدير ولا النسخ الاحتياطي). */
object AiKeyStore {
    private const val PREFS = "ai_settings"

    fun load(context: Context): AiSettings? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = p.getString("key", "").orEmpty()
        if (key.isBlank()) return null
        return AiSettings(
            providerId = p.getString("provider", "gemini").orEmpty(),
            apiKey = key,
            model = p.getString("model", "").orEmpty(),
            customBaseUrl = p.getString("base", "").orEmpty()
        )
    }

    fun save(context: Context, s: AiSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("provider", s.providerId).putString("key", s.apiKey.trim())
            .putString("model", s.model.trim()).putString("base", s.customBaseUrl.trim()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
