package com.arabicuieditor.data.github

import android.content.Context

/** يحفظ رمز GitHub في تخزين التطبيق الخاص (allowBackup=false فلا يُنسخ احتياطيًا). */
object GitHubAuthStore {
    private const val PREFS = "github_auth"
    private const val KEY = "token"

    fun token(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.let { com.arabicuieditor.data.SecretBox.open(it) }?.takeIf { it.isNotBlank() }

    fun save(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, com.arabicuieditor.data.SecretBox.seal(token)).apply()
    }

    /** رمز جهاز معلّق (لم تتم الموافقة عليه بعد)، يُستأنف بدل طلب رمز جديد إن لم ينتهِ. */
    fun pending(context: Context, nowMs: Long = System.currentTimeMillis()): DeviceCode? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = p.getString("dc", null) ?: return null
        val expiresAt = p.getLong("dc_exp", 0L)
        if (expiresAt - nowMs < 20_000) return null
        return DeviceCode(
            code, p.getString("dc_user", "").orEmpty(), p.getString("dc_uri", "").orEmpty(),
            p.getInt("dc_int", 5), ((expiresAt - nowMs) / 1000).toInt()
        )
    }

    fun savePending(context: Context, d: DeviceCode, nowMs: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("dc", d.deviceCode).putString("dc_user", d.userCode).putString("dc_uri", d.verificationUri)
            .putInt("dc_int", d.intervalSec).putLong("dc_exp", nowMs + d.expiresInSec * 1000L).apply()
    }

    fun clearPending(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("dc").remove("dc_user").remove("dc_uri").remove("dc_int").remove("dc_exp").apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
