package com.arabicuieditor.ui.editor

/**
 * سجل التراجع والإعادة بالاعتماد على لقطات كاملة من الحالة [T] (غير قابلة للتعديل).
 * اللقطات سهلة وموثوقة: لا عمليات عكسية يمكن أن تتناقض مع الحالة.
 *
 * دمج التعديلات المتتالية: تعديلان متتاليان بنفس [key] خلال [coalesceWindowMs] من آخر تعديل
 * يُحسبان خطوة واحدة (سحب عنصر، كتابة رقم، تحريك شريط...). بلا [key] لا دمج أبدًا.
 *
 * منطق نقي بلا اعتماد على Android؛ الساعة قابلة للحقن للاختبار.
 */
class EditHistory<T>(
    private val limit: Int = 100,
    private val coalesceWindowMs: Long = 700L,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()
    private var lastKey: String? = null
    private var lastTime = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** يسجّل أن الحالة كانت [before] قبل تعديل جديد. يمسح مسار الإعادة. */
    fun record(before: T, key: String? = null) {
        val t = now()
        val merge = key != null && key == lastKey && t - lastTime <= coalesceWindowMs
        lastKey = key
        lastTime = t
        if (!merge) {
            undoStack.addLast(before)
            while (undoStack.size > limit) undoStack.removeFirst()
        }
        redoStack.clear()
    }

    /** يعيد الحالة السابقة (ويحفظ [current] للإعادة)، أو null إن لم يوجد ما يُتراجع عنه. */
    fun undo(current: T): T? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        lastKey = null
        return previous
    }

    fun redo(current: T): T? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        lastKey = null
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastKey = null
    }
}
