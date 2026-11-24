package com.arabicuieditor.ads

/** يمنع تكرار الإعلان البيني: فاصل أدنى بين إعلانين (الافتراضي دقيقتان). منطق نقي قابل للاختبار. */
class AdThrottle(private val minGapMs: Long = 120_000L) {
    private var last = -1L

    fun canShow(nowMs: Long): Boolean = last < 0 || nowMs - last >= minGapMs

    fun markShown(nowMs: Long) { last = nowMs }
}
