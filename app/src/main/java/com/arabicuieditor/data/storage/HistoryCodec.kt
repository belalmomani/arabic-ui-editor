package com.arabicuieditor.data.storage

import org.json.JSONArray
import org.json.JSONObject

/** يحفظ لقطات التراجع/الإعادة (كل لقطة نص JSON لمشروع) في ملف واحد. منطق نقي بلا Android غير org.json. */
object HistoryCodec {
    const val MAX_UNDO = 15
    const val MAX_REDO = 5

    fun encode(undo: List<String>, redo: List<String>): String = JSONObject().apply {
        put("undo", JSONArray().also { a -> undo.forEach { a.put(it) } })
        put("redo", JSONArray().also { a -> redo.forEach { a.put(it) } })
    }.toString()

    /** يعيد null إن كان النص تالفًا. */
    fun decode(text: String): Pair<List<String>, List<String>>? = try {
        val o = JSONObject(text)
        fun list(key: String): List<String> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotEmpty() } }
        }
        list("undo") to list("redo")
    } catch (_: Exception) {
        null
    }
}
