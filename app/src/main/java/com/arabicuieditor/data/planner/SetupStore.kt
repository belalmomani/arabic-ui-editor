package com.arabicuieditor.data.planner

import android.content.Context

/** مشاريع الإنشاء التلقائي التي لم يُكمَل إدخال معلوماتها بعد (تُحفظ محليًا؛ لا تغيّر صيغة المشروع). */
object SetupStore {
    private const val PREFS = "setup_pending"
    private const val KEY = "ids"

    fun encode(ids: List<String>): String = ids.distinct().joinToString(",")
    fun decode(raw: String?): List<String> = raw.orEmpty().split(',').filter { it.isNotBlank() }

    fun pending(context: Context): List<String> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, ""))

    fun add(context: Context, id: String) = write(context, listOf(id) + pending(context))
    fun remove(context: Context, id: String) = write(context, pending(context) - id)

    private fun write(context: Context, ids: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(ids)).apply()
    }
}
