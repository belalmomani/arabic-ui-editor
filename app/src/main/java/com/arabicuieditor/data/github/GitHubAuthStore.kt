package com.arabicuieditor.data.github

import android.content.Context

/** يحفظ رمز GitHub في تخزين التطبيق الخاص (allowBackup=false فلا يُنسخ احتياطيًا). */
object GitHubAuthStore {
    private const val PREFS = "github_auth"
    private const val KEY = "token"

    fun token(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun save(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, token).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
