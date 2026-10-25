package com.arabicuieditor.data.model

/** عمليات نقية على قائمة شاشات المشروع (إنشاء، تسمية، نسخ، حذف). */
object ScreenOps {

    const val MAX_NAME_LENGTH = 60

    /** اسم شاشة غير مستعمل: [base] إن كان حرًّا، وإلا "[base] 2" و"[base] 3"... (يتجاهل الشاشة [excludeId]). */
    fun uniqueName(screens: List<UiScreen>, base: String, excludeId: String? = null): String {
        val clean = base.trim().take(MAX_NAME_LENGTH).ifBlank { "-" }
        val taken = screens.filter { it.id != excludeId }.map { it.name }.toSet()
        if (clean !in taken) return clean
        var n = 2
        while ("$clean $n" in taken) n++
        return "$clean $n"
    }

    fun rename(screens: List<UiScreen>, id: String, newName: String): List<UiScreen> {
        if (screens.none { it.id == id }) return screens
        val name = uniqueName(screens, newName, excludeId = id)
        return screens.map { if (it.id == id) it.copy(name = name) else it }
    }

    /** ينسخ شاشة (بعناصر ومعرّفات جديدة) ويضعها بعد الأصل. يعيد القائمة ومعرّف النسخة. */
    fun duplicate(screens: List<UiScreen>, id: String, copySuffix: String): Pair<List<UiScreen>, String>? {
        val index = screens.indexOfFirst { it.id == id }
        if (index < 0) return null
        val source = screens[index]
        val copy = source.copy(
            id = newId(),
            name = uniqueName(screens, "${source.name} $copySuffix"),
            elements = source.elements.map { ElementTree.reId(it) }
        )
        val out = screens.toMutableList()
        out.add(index + 1, copy)
        return out to copy.id
    }

    /** نتيجة الحذف: القائمة الجديدة، وشاشة البداية الجديدة، والشاشة التي يجب عرضها بعد الحذف. */
    data class DeleteResult(val screens: List<UiScreen>, val startScreenId: String?, val showScreenId: String?)

    /**
     * يحذف شاشة. لا يُسمح بحذف الشاشة الوحيدة (يعيد null). إن كانت المحذوفة هي شاشة البداية
     * تصبح أول شاشة متبقية هي البداية.
     */
    fun delete(screens: List<UiScreen>, id: String, startScreenId: String?): DeleteResult? {
        val index = screens.indexOfFirst { it.id == id }
        if (index < 0 || screens.size <= 1) return null
        val remaining = screens.filterNot { it.id == id }
        val effectiveStart = screens.firstOrNull { it.id == startScreenId }?.id ?: screens.first().id
        val newStart = if (effectiveStart == id) remaining.first().id else effectiveStart
        val show = remaining[(index - 1).coerceAtLeast(0).coerceAtMost(remaining.lastIndex)].id
        return DeleteResult(remaining, newStart, show)
    }
}
