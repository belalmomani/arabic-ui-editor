package com.arabicuieditor.ui.editor.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.arabicuieditor.data.model.UiScreen
import kotlin.math.hypot

private const val HANDLE_OUTER_TOUCH_DP = 24f
private const val HANDLE_INNER_TOUCH_DP = 12f
private const val HANDLE_VISUAL_DP = 7f
private const val EDGE_HANDLE_MIN_SIDE_DP = 96f
private const val ELEMENT_HIT_SLOP_DP = 6f

private val CanvasBackground = Color(0xFF9AA3AD)
private val SelectionColor = Color(0xFF1B8CFF)

/** ماذا يفعل اللمس الحالي. يُحدَّد لحظة وضع الإصبع. */
private sealed interface GestureTarget {
    data object Pan : GestureTarget
    data class Move(val id: String, val start: DesignRect) : GestureTarget
    data class Resize(val id: String, val start: DesignRect, val handle: HandleType) : GestureTarget
}

/**
 * مساحة التصميم: إطار شاشة أندرويد يُرسم فعليًا من نموذج البيانات، مع إيماءات:
 * - إصبع على عنصر: تحديده وتحريكه.
 * - إصبع على مقبض العنصر المحدد: تغيير الحجم.
 * - إصبع على فراغ: تحريك مساحة العمل (والنقر يلغي التحديد).
 * - إصبعان: تكبير/تصغير وتحريك.
 *
 * كل الإحداثيات تُقرأ من [screen] ولا تُخزَّن هنا؛ التعديلات تُرسل للأعلى
 * عبر [onElementRectChange] فتنتقل إلى نموذج المشروع والحفظ التلقائي.
 */
@Composable
fun DesignCanvas(
    screen: UiScreen,
    selectedId: String?,
    viewport: Viewport,
    onViewportChange: (Viewport) -> Unit,
    onSelect: (String?) -> Unit,
    onElementRectChange: (String, DesignRect) -> Unit,
    onSizeChange: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestScreen by rememberUpdatedState(screen)
    val latestSelected by rememberUpdatedState(selectedId)
    val latestViewport by rememberUpdatedState(viewport)
    val latestOnViewport by rememberUpdatedState(onViewportChange)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestOnRect by rememberUpdatedState(onElementRectChange)

    // مساحة التصميم يسار-يمين دائمًا (إحداثيات مطلقة)، بغض النظر عن اتجاه التطبيق RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .clipToBounds()
                .background(CanvasBackground)
                .onSizeChanged(onSizeChange)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val dens = density
                        val touchSlop = viewConfiguration.touchSlop
                        var vp = latestViewport
                        val scr = latestScreen
                        val frameW = scr.width
                        val frameH = scr.height
                        val selectedBefore = latestSelected

                        val rects = scr.elements.map { it.id to CanvasMath.resolveRect(it, frameW, frameH) }
                        val target = decideTarget(
                            rects = rects,
                            selectedId = selectedBefore,
                            vp = vp,
                            density = dens,
                            px = down.position.x,
                            py = down.position.y
                        )
                        if (target is GestureTarget.Move && target.id != selectedBefore) {
                            latestOnSelect(target.id)
                        }

                        var totalX = 0f
                        var totalY = 0f
                        var appliedPanX = 0f
                        var appliedPanY = 0f
                        var moved = false
                        var multiTouch = false

                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                multiTouch = true
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                if (zoom != 1f || pan != Offset.Zero) {
                                    vp = CanvasMath.transform(vp, zoom, pan.x, pan.y, centroid.x, centroid.y)
                                    latestOnViewport(vp)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (!multiTouch && pressed.size == 1) {
                                val change = pressed[0]
                                val delta = change.positionChange()
                                totalX += delta.x
                                totalY += delta.y
                                if (!moved && hypot(totalX, totalY) > touchSlop) moved = true
                                if (moved) {
                                    val pxPerDp = dens * vp.scale
                                    when (target) {
                                        is GestureTarget.Resize -> latestOnRect(
                                            target.id,
                                            CanvasMath.resize(
                                                target.start, target.handle,
                                                totalX / pxPerDp, totalY / pxPerDp
                                            )
                                        )

                                        is GestureTarget.Move -> latestOnRect(
                                            target.id,
                                            CanvasMath.move(
                                                target.start,
                                                totalX / pxPerDp, totalY / pxPerDp,
                                                frameW, frameH
                                            )
                                        )

                                        GestureTarget.Pan -> {
                                            vp = vp.copy(
                                                offsetX = vp.offsetX + (totalX - appliedPanX),
                                                offsetY = vp.offsetY + (totalY - appliedPanY)
                                            )
                                            appliedPanX = totalX
                                            appliedPanY = totalY
                                            latestOnViewport(vp)
                                        }
                                    }
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // نقرة على فراغ بلا سحب ولا إصبعين: إلغاء التحديد.
                        if (!moved && !multiTouch && target is GestureTarget.Pan) {
                            latestOnSelect(null)
                        }
                    }
                }
        ) {
            // إطار الشاشة المصمَّمة: يُكبَّر ويُزاح بـ graphicsLayer، وتُرسم العناصر بوحدة dp.
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(screen.width.dp, screen.height.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = viewport.scale
                        scaleY = viewport.scale
                        translationX = viewport.offsetX
                        translationY = viewport.offsetY
                        clip = true
                    }
                    .background(Color.White)
            ) {
                screen.elements.forEach { element ->
                    key(element.id) {
                        val rect = CanvasMath.resolveRect(element, screen.width, screen.height)
                        ElementView(
                            element = element,
                            modifier = Modifier
                                .absoluteOffset(rect.x.dp, rect.y.dp)
                                .size(rect.w.dp, rect.h.dp)
                                .clipToBounds()
                        )
                    }
                }
            }

            SelectionOverlay(screen = screen, selectedId = selectedId, viewport = viewport)
        }
    }
}

/** يحدد هدف اللمس: مقبض العنصر المحدد، أو أعلى عنصر تحت الإصبع، أو تحريك المساحة. */
private fun decideTarget(
    rects: List<Pair<String, DesignRect>>,
    selectedId: String?,
    vp: Viewport,
    density: Float,
    px: Float,
    py: Float
): GestureTarget {
    val selected = rects.firstOrNull { it.first == selectedId }
    if (selected != null) {
        val sr = CanvasMath.toScreenRect(vp, density, selected.second)
        val handles = CanvasMath.handlePositions(sr, EDGE_HANDLE_MIN_SIDE_DP * density)
        val handle = CanvasMath.hitHandle(
            handles, sr, px, py,
            outerRadius = HANDLE_OUTER_TOUCH_DP * density,
            innerRadius = HANDLE_INNER_TOUCH_DP * density
        )
        if (handle != null) return GestureTarget.Resize(selected.first, selected.second, handle)
    }
    val designX = CanvasMath.toDesignX(vp, density, px)
    val designY = CanvasMath.toDesignY(vp, density, py)
    val slopDesign = ELEMENT_HIT_SLOP_DP / vp.scale
    val hit = CanvasMath.hitTest(rects, designX, designY, slopDesign)
    if (hit != null) {
        return GestureTarget.Move(hit, rects.first { it.first == hit }.second)
    }
    return GestureTarget.Pan
}

/** حدود الإطار وإطار التحديد والمقابض، تُرسم بحجم ثابت على الشاشة مهما كان التكبير. */
@Composable
private fun SelectionOverlay(screen: UiScreen, selectedId: String?, viewport: Viewport) {
    val density = LocalDensity.current.density
    Canvas(Modifier.fillMaxSize()) {
        val frame = CanvasMath.toScreenRect(
            viewport, density,
            DesignRect(0f, 0f, screen.width, screen.height)
        )
        drawRect(
            color = Color(0x33000000),
            topLeft = Offset(frame.left, frame.top),
            size = Size(frame.width, frame.height),
            style = Stroke(1.dp.toPx())
        )

        val selected = screen.elements.firstOrNull { it.id == selectedId } ?: return@Canvas
        val rect = CanvasMath.toScreenRect(
            viewport, density,
            CanvasMath.resolveRect(selected, screen.width, screen.height)
        )
        drawRect(
            color = SelectionColor,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            style = Stroke(2.dp.toPx())
        )
        val radius = HANDLE_VISUAL_DP.dp.toPx()
        CanvasMath.handlePositions(rect, EDGE_HANDLE_MIN_SIDE_DP * density).forEach { h ->
            val center = Offset(h.x, h.y)
            drawCircle(Color.White, radius, center)
            drawCircle(SelectionColor, radius, center, style = Stroke(2.dp.toPx()))
        }
    }
}
