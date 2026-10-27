package com.arabicuieditor.ui.editor.canvas

import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.SizeSpec
import com.arabicuieditor.data.model.UiElement
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * منطق هندسي بحت (بلا أي اعتماد على Android أو Compose) لمساحة التصميم،
 * كي يُختبر بسهولة ويبقى منفصلًا عن واجهة المحرر.
 *
 * الوحدات:
 *  - إحداثيات التصميم (design): dp داخل إطار الشاشة، الأصل أعلى يسار الإطار.
 *  - إحداثيات الشاشة (screen): بكسلات داخل مساحة العرض.
 *  التحويل: screen = offset + design * density * scale
 */

/** مستطيل بإحداثيات التصميم (dp). */
data class DesignRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h

    fun contains(px: Float, py: Float, slop: Float = 0f): Boolean =
        px >= x - slop && px <= right + slop && py >= y - slop && py <= bottom + slop
}

/** مستطيل بإحداثيات الشاشة (بكسل). */
data class ScreenRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(px: Float, py: Float): Boolean = px in left..right && py in top..bottom
}

/** حالة العرض: مقياس التكبير وإزاحة الإطار بالبكسل. */
data class Viewport(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f)

enum class HandleType {
    TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, TOP, RIGHT, BOTTOM, LEFT
}

data class HandlePos(val type: HandleType, val x: Float, val y: Float)

object CanvasMath {
    const val MIN_SCALE = 0.25f
    const val MAX_SCALE = 4f
    const val MIN_ELEMENT_SIZE = 16f

    // ---- التحويلات ----

    fun toScreenX(vp: Viewport, density: Float, designX: Float): Float =
        vp.offsetX + designX * density * vp.scale

    fun toScreenY(vp: Viewport, density: Float, designY: Float): Float =
        vp.offsetY + designY * density * vp.scale

    fun toDesignX(vp: Viewport, density: Float, screenX: Float): Float =
        (screenX - vp.offsetX) / (density * vp.scale)

    fun toDesignY(vp: Viewport, density: Float, screenY: Float): Float =
        (screenY - vp.offsetY) / (density * vp.scale)

    fun toScreenRect(vp: Viewport, density: Float, r: DesignRect): ScreenRect {
        val left = toScreenX(vp, density, r.x)
        val top = toScreenY(vp, density, r.y)
        return ScreenRect(left, top, left + r.w * density * vp.scale, top + r.h * density * vp.scale)
    }

    // ---- العرض: ملاءمة وتكبير وتحريك ----

    /** يضبط العرض ليظهر الإطار كاملًا في مركز مساحة العرض. */
    fun fit(
        viewW: Float,
        viewH: Float,
        frameW: Float,
        frameH: Float,
        density: Float,
        marginPx: Float
    ): Viewport {
        val availW = max(1f, viewW - 2 * marginPx)
        val availH = max(1f, viewH - 2 * marginPx)
        val scale = min(availW / (frameW * density), availH / (frameH * density))
            .coerceIn(MIN_SCALE, MAX_SCALE)
        val contentW = frameW * density * scale
        val contentH = frameH * density * scale
        return Viewport(scale, (viewW - contentW) / 2f, (viewH - contentH) / 2f)
    }

    /**
     * يطبّق تحريكًا ثم تكبيرًا حول نقطة (مركز الأصابع)، فتبقى النقطة التصميمية
     * تحت الأصابع ثابتة أثناء القرص.
     */
    fun transform(
        vp: Viewport,
        zoom: Float,
        panX: Float,
        panY: Float,
        centroidX: Float,
        centroidY: Float
    ): Viewport {
        val movedX = vp.offsetX + panX
        val movedY = vp.offsetY + panY
        val newScale = (vp.scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val factor = newScale / vp.scale
        return Viewport(
            scale = newScale,
            offsetX = centroidX - (centroidX - movedX) * factor,
            offsetY = centroidY - (centroidY - movedY) * factor
        )
    }

    // ---- أبعاد العناصر ----

    fun resolveSize(spec: SizeSpec, available: Float): Float = when (spec.mode) {
        SizeMode.FIXED -> spec.value
        SizeMode.MATCH_PARENT -> available
        SizeMode.FRACTION -> available * spec.value.coerceIn(1f, 100f) / 100f
        SizeMode.WRAP_CONTENT -> if (spec.value > 0f) spec.value else MIN_ELEMENT_SIZE * 4
    }

    /** مستطيل العنصر بإحداثيات التصميم بعد حل أنماط الأبعاد. */
    fun resolveRect(element: UiElement, frameW: Float, frameH: Float): DesignRect {
        val l = element.layout
        return DesignRect(
            x = l.x,
            y = l.y,
            w = max(MIN_ELEMENT_SIZE, resolveSize(l.width, frameW)),
            h = max(MIN_ELEMENT_SIZE, resolveSize(l.height, frameH))
        )
    }

    // ---- الاختيار ----

    /**
     * يعيد معرّف أعلى عنصر عند النقطة، أو null. ترتيب القائمة هو ترتيب الطبقات
     * (الأخير فوق). [slop] هامش إضافي يسهّل لمس العناصر الرفيعة.
     */
    fun hitTest(rects: List<Pair<String, DesignRect>>, x: Float, y: Float, slop: Float): String? {
        for (i in rects.indices.reversed()) {
            if (rects[i].second.contains(x, y, slop)) return rects[i].first
        }
        return null
    }

    // ---- الدوران والتكرار ----

    /** يدور بالنقطة (x,y) حول (cx,cy) بزاوية [degrees] باتجاه عقارب الساعة (محور y نحو الأسفل). */
    fun rotatePoint(x: Float, y: Float, cx: Float, cy: Float, degrees: Float): Pair<Float, Float> {
        val rad = Math.toRadians(degrees.toDouble())
        val c = kotlin.math.cos(rad).toFloat()
        val s = kotlin.math.sin(rad).toFloat()
        val dx = x - cx
        val dy = y - cy
        return (cx + dx * c - dy * s) to (cy + dx * s + dy * c)
    }

    /** زاوية الاتجاه من (cx,cy) إلى (px,py) بالدرجات؛ صفر نحو اليمين وتزيد مع عقارب الساعة. */
    fun angleDegrees(cx: Float, cy: Float, px: Float, py: Float): Float =
        Math.toDegrees(kotlin.math.atan2((py - cy).toDouble(), (px - cx).toDouble())).toFloat()

    /** يحوّل أي زاوية إلى 0..359. */
    fun normalizeDegrees(degrees: Float): Int {
        val r = Math.round(degrees) % 360
        return if (r < 0) r + 360 else r
    }

    /** يلتقط الزاوية لأقرب مضاعف لـ [step] إن كانت ضمن [tolerance] منه (لتسهيل الاستقامة وزوايا 45/90). */
    fun snapAngle(degrees: Int, step: Int = 15, tolerance: Int = 4): Int {
        val nearest = Math.round(degrees.toFloat() / step) * step
        return if (kotlin.math.abs(nearest - degrees) <= tolerance) ((nearest % 360) + 360) % 360 else degrees
    }

    /**
     * مثل [hitTest] لكن يراعي دوران كل عنصر: تُدار النقطة عكسيًا حول مركز إطار العنصر
     * قبل اختبار احتوائها. [rotationOf] يعيد دوران العنصر بالدرجات.
     */
    fun hitTestRotated(
        rects: List<Pair<String, DesignRect>>,
        rotationOf: (String) -> Int,
        x: Float,
        y: Float,
        slop: Float
    ): String? {
        for (i in rects.indices.reversed()) {
            val (id, r) = rects[i]
            val rot = rotationOf(id)
            val (lx, ly) = if (rot == 0) x to y
            else rotatePoint(x, y, r.x + r.w / 2f, r.y + r.h / 2f, -rot.toFloat())
            if (r.contains(lx, ly, slop)) return id
        }
        return null
    }

    // ---- المقابض ----

    /**
     * مواضع المقابض بالبكسل: أربعة أركان دائمًا، وأضلاع الوسط فقط إن كان الضلع
     * طويلًا بما يكفي كي لا تتزاحم المقابض على العناصر الصغيرة.
     */
    fun handlePositions(r: ScreenRect, minEdgeSidePx: Float): List<HandlePos> {
        val cx = (r.left + r.right) / 2f
        val cy = (r.top + r.bottom) / 2f
        val list = mutableListOf(
            HandlePos(HandleType.TOP_LEFT, r.left, r.top),
            HandlePos(HandleType.TOP_RIGHT, r.right, r.top),
            HandlePos(HandleType.BOTTOM_RIGHT, r.right, r.bottom),
            HandlePos(HandleType.BOTTOM_LEFT, r.left, r.bottom)
        )
        if (r.width >= minEdgeSidePx) {
            list += HandlePos(HandleType.TOP, cx, r.top)
            list += HandlePos(HandleType.BOTTOM, cx, r.bottom)
        }
        if (r.height >= minEdgeSidePx) {
            list += HandlePos(HandleType.LEFT, r.left, cy)
            list += HandlePos(HandleType.RIGHT, r.right, cy)
        }
        return list
    }

    /**
     * أقرب مقبض للمسة. خارج مستطيل العنصر يُقبل نصف قطر واسع [outerRadius]،
     * وداخله يضيق إلى [innerRadius] كي تبقى مساحة العنصر قابلة للسحب (للتحريك)
     * حتى لو كان صغيرًا أو رفيعًا.
     */
    fun hitHandle(
        handles: List<HandlePos>,
        rect: ScreenRect,
        px: Float,
        py: Float,
        outerRadius: Float,
        innerRadius: Float
    ): HandleType? {
        val radius = if (rect.contains(px, py)) innerRadius else outerRadius
        var best: HandlePos? = null
        var bestDist = Float.MAX_VALUE
        for (h in handles) {
            val d = hypot(h.x - px, h.y - py)
            if (d <= radius && d < bestDist) {
                best = h
                bestDist = d
            }
        }
        return best?.type
    }

    // ---- التعديل ----

    /** تغيير الحجم من مقبض، بإزاحة كلية (dx, dy) بوحدة dp منذ بداية اللمس. */
    fun resize(
        start: DesignRect,
        handle: HandleType,
        dx: Float,
        dy: Float,
        minSize: Float = MIN_ELEMENT_SIZE
    ): DesignRect {
        val movesLeft = handle == HandleType.TOP_LEFT || handle == HandleType.LEFT || handle == HandleType.BOTTOM_LEFT
        val movesRight = handle == HandleType.TOP_RIGHT || handle == HandleType.RIGHT || handle == HandleType.BOTTOM_RIGHT
        val movesTop = handle == HandleType.TOP_LEFT || handle == HandleType.TOP || handle == HandleType.TOP_RIGHT
        val movesBottom = handle == HandleType.BOTTOM_LEFT || handle == HandleType.BOTTOM || handle == HandleType.BOTTOM_RIGHT

        var x = start.x
        var y = start.y
        var w = start.w
        var h = start.h
        if (movesLeft) {
            val nw = max(minSize, start.w - dx)
            x = start.x + (start.w - nw)
            w = nw
        }
        if (movesRight) w = max(minSize, start.w + dx)
        if (movesTop) {
            val nh = max(minSize, start.h - dy)
            y = start.y + (start.h - nh)
            h = nh
        }
        if (movesBottom) h = max(minSize, start.h + dy)
        return DesignRect(round(x), round(y), round(w), round(h))
    }

    /** تحريك العنصر مع إبقاء جزء منه ظاهرًا داخل الإطار كي لا يضيع خارجه. */
    fun move(
        start: DesignRect,
        dx: Float,
        dy: Float,
        frameW: Float,
        frameH: Float,
        keepVisible: Float = MIN_ELEMENT_SIZE
    ): DesignRect {
        val nx = (start.x + dx).coerceIn(keepVisible - start.w, frameW - keepVisible)
        val ny = (start.y + dy).coerceIn(keepVisible - start.h, frameH - keepVisible)
        return DesignRect(round(nx), round(ny), start.w, start.h)
    }

    private fun round(v: Float): Float = v.roundToInt().toFloat()
}
