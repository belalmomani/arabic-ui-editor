package com.arabicuieditor.ui.editor.canvas

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** خط إرشادي يُرسم أثناء السحب. [vertical] أي خط رأسي عند x=[pos]. [spacing] لخطوط التباعد المتساوي. */
data class Guide(
    val vertical: Boolean,
    val pos: Float,
    val start: Float,
    val end: Float,
    val spacing: Boolean = false
)

data class SnapResult(val rect: DesignRect, val guides: List<Guide>)

/** إعدادات الالتقاط: [objects] للعناصر والحواف والمراكز، و[grid] حجم الشبكة بالـ dp (0 = بلا شبكة). */
data class SnapSettings(val objects: Boolean = true, val grid: Int = 0)

enum class AlignEdge { LEFT, CENTER_H, RIGHT, TOP, CENTER_V, BOTTOM }

/**
 * المحاذاة الذكية: التقاط حواف ومراكز العناصر والشاشة، والتباعد المتساوي، والشبكة،
 * وأوامر المحاذاة والتوزيع. نقية بلا اعتماد على Android.
 *
 * كل الإحداثيات مطلقة بوحدة dp على الشاشة (كما في [LayoutResult.frames]).
 */
object SmartAlign {

    /** مسافة الالتقاط على الشاشة بالـ dp؛ تُقسَم على التكبير لتبقى ثابتة بصريًا. */
    const val THRESHOLD_DP = 6f
    private const val EPS = 0.5f

    /** إطارات يلتقط إليها العنصر [id]: كل عناصر الشاشة عدا نفسه وأحفاده، وحدود الشاشة. */
    fun targetsFor(layout: LayoutResult, id: String): List<DesignRect> {
        val own = HashSet<String>().also { it += id }
        // الأحفاد: كل عنصر سلسلة آبائه تمر بـ id.
        for (other in layout.frames.keys) {
            var p = layout.parentOf[other]
            while (p != null) {
                if (p == id) {
                    own += other
                    break
                }
                p = layout.parentOf[p]
            }
        }
        return layout.frames.filterKeys { it !in own }.values.toList()
    }

    // ---- الالتقاط على محور واحد ----

    private class Candidate(val delta: Float, val target: Float, val spacing: Boolean)

    private fun best(c: List<Candidate>, threshold: Float): Candidate? =
        c.filter { abs(it.delta) <= threshold }.minByOrNull { abs(it.delta) }

    private fun gridDelta(value: Float, grid: Int): Float =
        if (grid <= 0) 0f else (value / grid).roundToInt() * grid - value

    /** مرشحات التقاط الخطوط [lines] (يسار/مركز/يمين مثلًا) إلى خطوط الأهداف. */
    private fun lineCandidates(lines: List<Float>, targets: List<Float>): List<Candidate> {
        val out = ArrayList<Candidate>()
        for (l in lines) for (t in targets) out += Candidate(t - l, t, false)
        return out
    }

    private fun xLines(r: DesignRect) = listOf(r.x, r.x + r.w / 2f, r.right)
    private fun yLines(r: DesignRect) = listOf(r.y, r.y + r.h / 2f, r.bottom)

    /**
     * مرشحات التباعد المتساوي على المحور الأفقي (أو الرأسي إن كان [horizontal]=false) للعنصر [m]:
     * بين جارين بفجوتين متساويتين، أو بنفس فجوة جارين متجاورين. تعيد إزاحة موضع بداية العنصر.
     */
    private fun gapCandidates(m: DesignRect, targets: List<DesignRect>, horizontal: Boolean): List<Candidate> {
        fun start(r: DesignRect) = if (horizontal) r.x else r.y
        fun end(r: DesignRect) = if (horizontal) r.right else r.bottom
        fun size(r: DesignRect) = if (horizontal) r.w else r.h
        fun overlapsCross(r: DesignRect) =
            if (horizontal) min(r.bottom, m.bottom) - max(r.y, m.y) > 0f
            else min(r.right, m.right) - max(r.x, m.x) > 0f

        val near = targets.filter { overlapsCross(it) }.sortedBy { start(it) }
        val out = ArrayList<Candidate>()
        val ms = start(m)
        val mid = ms + size(m) / 2f

        // بين جارين: فجوتان متساويتان.
        val left = near.filter { end(it) <= mid }.maxByOrNull { end(it) }
        val right = near.filter { start(it) >= mid }.minByOrNull { start(it) }
        if (left != null && right != null) {
            val free = start(right) - end(left) - size(m)
            if (free >= 0f) {
                val target = end(left) + free / 2f
                out += Candidate(target - ms, target, true)
            }
        }
        // بنفس فجوة جارين متجاورين.
        for (i in 0 until near.size - 1) {
            val a = near[i]
            val b = near[i + 1]
            val gap = start(b) - end(a)
            if (gap <= 0f) continue
            val after = end(b) + gap
            out += Candidate(after - ms, after, true)
            val before = start(a) - gap - size(m)
            out += Candidate(before - ms, before, true)
        }
        return out
    }

    // ---- التحريك ----

    /**
     * يلتقط مستطيلًا يتحرك: حافته ومركزه إلى حواف ومراكز الأهداف وحدود الشاشة [screen]،
     * ثم إلى التباعد المتساوي، ثم إلى الشبكة إن لم يلتقط شيئًا.
     * [threshold] بالـ dp التصميمية (اقسم 6 على التكبير).
     */
    fun snapMove(
        moving: DesignRect,
        targets: List<DesignRect>,
        screen: DesignRect,
        threshold: Float,
        settings: SnapSettings
    ): SnapResult {
        var xHit: Candidate? = null
        var yHit: Candidate? = null
        if (settings.objects) {
            val all = targets + screen
            val tx = all.flatMap { xLines(it) }
            val ty = all.flatMap { yLines(it) }
            xHit = best(lineCandidates(xLines(moving), tx) + gapCandidates(moving, targets, true), threshold)
            yHit = best(lineCandidates(yLines(moving), ty) + gapCandidates(moving, targets, false), threshold)
        }
        val dx = xHit?.delta ?: gridDelta(moving.x, settings.grid)
        val dy = yHit?.delta ?: gridDelta(moving.y, settings.grid)
        val out = moving.copy(x = moving.x + dx, y = moving.y + dy)
        return SnapResult(out, if (settings.objects) guidesFor(out, targets + screen, xHit, yHit) else emptyList())
    }

    // ---- تغيير الحجم ----

    /** يلتقط الحواف المتحركة فقط لمستطيل يُغيَّر حجمه بالمقبض [handle]. */
    fun snapResize(
        rect: DesignRect,
        handle: HandleType,
        targets: List<DesignRect>,
        screen: DesignRect,
        threshold: Float,
        settings: SnapSettings,
        minSize: Float = CanvasMath.MIN_ELEMENT_SIZE
    ): SnapResult {
        val movesLeft = handle == HandleType.TOP_LEFT || handle == HandleType.LEFT || handle == HandleType.BOTTOM_LEFT
        val movesRight = handle == HandleType.TOP_RIGHT || handle == HandleType.RIGHT || handle == HandleType.BOTTOM_RIGHT
        val movesTop = handle == HandleType.TOP_LEFT || handle == HandleType.TOP || handle == HandleType.TOP_RIGHT
        val movesBottom = handle == HandleType.BOTTOM_LEFT || handle == HandleType.BOTTOM || handle == HandleType.BOTTOM_RIGHT

        val all = targets + screen
        val tx = all.flatMap { xLines(it) }
        val ty = all.flatMap { yLines(it) }

        fun snapEdge(value: Float, lines: List<Float>): Candidate? {
            val hit = if (settings.objects) best(lineCandidates(listOf(value), lines), threshold) else null
            if (hit != null) return hit
            val g = gridDelta(value, settings.grid)
            return if (settings.grid > 0) Candidate(g, value + g, false) else null
        }

        var x = rect.x
        var y = rect.y
        var r = rect.right
        var b = rect.bottom
        var xHit: Candidate? = null
        var yHit: Candidate? = null
        if (movesLeft) snapEdge(x, tx)?.let { if (r - (x + it.delta) >= minSize) { x += it.delta; xHit = it } }
        if (movesRight) snapEdge(r, tx)?.let { if (it.target + 0f - x >= minSize) { r += it.delta; xHit = it } }
        if (movesTop) snapEdge(y, ty)?.let { if (b - (y + it.delta) >= minSize) { y += it.delta; yHit = it } }
        if (movesBottom) snapEdge(b, ty)?.let { if (it.target - y >= minSize) { b += it.delta; yHit = it } }
        val out = DesignRect(x, y, r - x, b - y)
        return SnapResult(out, if (settings.objects) guidesFor(out, all, xHit, yHit) else emptyList())
    }

    // ---- الخطوط الإرشادية ----

    /** خطوط الالتقاط: كل خط هدف يطابق حافة أو مركز المستطيل بعد الالتقاط. */
    private fun guidesFor(r: DesignRect, targets: List<DesignRect>, xHit: Candidate?, yHit: Candidate?): List<Guide> {
        val guides = ArrayList<Guide>()
        if (xHit != null) {
            if (xHit.spacing) {
                guides += Guide(true, r.x, r.y, r.bottom, spacing = true)
                guides += Guide(true, r.right, r.y, r.bottom, spacing = true)
            } else {
                for (t in targets) for (tl in xLines(t)) for (ml in xLines(r)) {
                    if (abs(tl - ml) < EPS) guides += Guide(true, tl, min(t.y, r.y), max(t.bottom, r.bottom))
                }
            }
        }
        if (yHit != null) {
            if (yHit.spacing) {
                guides += Guide(false, r.y, r.x, r.right, spacing = true)
                guides += Guide(false, r.bottom, r.x, r.right, spacing = true)
            } else {
                for (t in targets) for (tl in yLines(t)) for (ml in yLines(r)) {
                    if (abs(tl - ml) < EPS) guides += Guide(false, tl, min(t.x, r.x), max(t.right, r.right))
                }
            }
        }
        return guides.distinct()
    }

    // ---- أوامر المحاذاة والتوزيع ----

    /** يحاذي [frame] داخل [container] على الحافة أو المركز المطلوب. */
    fun alignIn(frame: DesignRect, container: DesignRect, edge: AlignEdge): DesignRect = when (edge) {
        AlignEdge.LEFT -> frame.copy(x = container.x)
        AlignEdge.CENTER_H -> frame.copy(x = container.x + (container.w - frame.w) / 2f)
        AlignEdge.RIGHT -> frame.copy(x = container.right - frame.w)
        AlignEdge.TOP -> frame.copy(y = container.y)
        AlignEdge.CENTER_V -> frame.copy(y = container.y + (container.h - frame.h) / 2f)
        AlignEdge.BOTTOM -> frame.copy(y = container.bottom - frame.h)
    }

    /**
     * يوزّع الإطارات بفجوات متساوية على محور واحد، مع بقاء الأول والأخير في مكانيهما.
     * يحتاج 3 عناصر على الأقل؛ وإلا يعيد خريطة فارغة. يعيد الإطارات التي تغيّرت فقط.
     */
    fun distribute(frames: List<Pair<String, DesignRect>>, horizontal: Boolean): Map<String, DesignRect> {
        if (frames.size < 3) return emptyMap()
        val sorted = frames.sortedBy { if (horizontal) it.second.x else it.second.y }
        val first = sorted.first().second
        val last = sorted.last().second
        val span = if (horizontal) last.right - first.x else last.bottom - first.y
        val used = sorted.sumOf { (if (horizontal) it.second.w else it.second.h).toDouble() }.toFloat()
        val gap = (span - used) / (sorted.size - 1)
        val out = LinkedHashMap<String, DesignRect>()
        var cursor = if (horizontal) first.x else first.y
        for ((id, r) in sorted) {
            val moved = if (horizontal) r.copy(x = cursor) else r.copy(y = cursor)
            if (moved != r) out[id] = moved
            cursor += (if (horizontal) r.w else r.h) + gap
        }
        return out
    }
}
