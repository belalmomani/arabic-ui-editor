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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.arabicuieditor.data.model.ElementTree
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
    /** [free] صحيح إن كان العنصر حر الموضع (أبوه بتخطيط حر) فيتحرك مباشرة أثناء السحب. */
    data class Move(val id: String, val start: DesignRect, val free: Boolean) : GestureTarget
    data class Resize(val id: String, val start: DesignRect, val handle: HandleType) : GestureTarget
}

/**
 * مساحة التصميم: إطار شاشة أندرويد يُرسم فعليًا من نموذج البيانات، مع إيماءات:
 * - إصبع على عنصر: تحديده وتحريكه.
 * - إصبع على مقبض العنصر المحدد: تغيير الحجم.
 * - إصبع على فراغ: تحريك مساحة العمل (والنقر يلغي التحديد).
 * - إصبعان: تكبير/تصغير وتحريك.
 *
 * كل الإحداثيات تُقرأ من [layout] (إطارات محسوبة لكل العناصر بما فيها المتداخلة) ولا تُخزَّن هنا؛
 * التعديلات تُرسل للأعلى عبر [onElementRectChange] فتنتقل إلى نموذج المشروع والحفظ التلقائي.
 *
 * - العنصر الحر (أبوه بتخطيط حر) يتحرك مباشرة مع الإصبع. عنصر الصف/العمود/الصندوق يظهر له ظل
 *   أثناء السحب، وعند الرفع يُستدعى [onElementDrop] ليُنقل إلى الحاوية أو الترتيب تحت الإصبع.
 * - [editable] false عندما تكون المعاينة بحجم غير حجم التصميم: التحديد والتحريك والتكبير فقط، بلا تعديل بالسحب.
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
    modifier: Modifier = Modifier,
    layout: LayoutResult = remember(screen) { LayoutEngine.layout(screen) },
    editable: Boolean = true,
    onElementDrop: ((String, Float, Float) -> Unit)? = null
) {
    val latestLayout by rememberUpdatedState(layout)
    val latestEditable by rememberUpdatedState(editable)
    val latestOnDrop by rememberUpdatedState(onElementDrop)
    var ghost by remember { mutableStateOf<DesignRect?>(null) }
    val elementsById = remember(screen) { ElementTree.flatten(screen.elements).associateBy { it.id } }
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
                        val lay = latestLayout
                        val frameW = lay.width
                        val frameH = lay.height
                        val selectedBefore = latestSelected

                        val rects = lay.paintOrder.mapNotNull { id -> lay.frames[id]?.let { id to it } }
                        val target = decideTarget(
                            rects = rects,
                            freeIds = lay.freeIds,
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
                        var lastX = down.position.x
                        var lastY = down.position.y
                        // بلا تعديل (معاينة بحجم مختلف): كل سحب يحرّك مساحة العمل، والنقر يحدد فقط.
                        val dragTarget = if (latestEditable) target else GestureTarget.Pan

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
                                lastX = change.position.x
                                lastY = change.position.y
                                val delta = change.positionChange()
                                totalX += delta.x
                                totalY += delta.y
                                if (!moved && hypot(totalX, totalY) > touchSlop) moved = true
                                if (moved) {
                                    val pxPerDp = dens * vp.scale
                                    when (dragTarget) {
                                        is GestureTarget.Resize -> latestOnRect(
                                            dragTarget.id,
                                            CanvasMath.resize(
                                                dragTarget.start, dragTarget.handle,
                                                totalX / pxPerDp, totalY / pxPerDp
                                            )
                                        )

                                        is GestureTarget.Move -> {
                                            val next = CanvasMath.move(
                                                dragTarget.start,
                                                totalX / pxPerDp, totalY / pxPerDp,
                                                frameW, frameH
                                            )
                                            if (dragTarget.free) latestOnRect(dragTarget.id, next) else ghost = next
                                        }

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

                        ghost = null
                        // نقرة على فراغ بلا سحب ولا إصبعين: إلغاء التحديد.
                        if (!moved && !multiTouch && target is GestureTarget.Pan) {
                            latestOnSelect(null)
                        }
                        // رفع عنصر بعد سحبه: المحرر يقرر نقله إلى حاوية أو إعادة ترتيبه.
                        if (moved && !multiTouch && dragTarget is GestureTarget.Move) {
                            latestOnDrop?.invoke(
                                dragTarget.id,
                                CanvasMath.toDesignX(vp, dens, lastX),
                                CanvasMath.toDesignY(vp, dens, lastY)
                            )
                        }
                    }
                }
        ) {
            // إطار الشاشة المصمَّمة: يُكبَّر ويُزاح بـ graphicsLayer، وتُرسم العناصر بوحدة dp.
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(layout.width.dp, layout.height.dp)
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
                // كل العناصر (بما فيها المتداخلة) تُرسم مسطّحة بإطاراتها المحسوبة، الأب قبل أبنائه.
                layout.paintOrder.forEach { id ->
                    val element = elementsById[id]
                    val rect = layout.frames[id]
                    if (element != null && rect != null) {
                        key(id) {
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
            }

            SelectionOverlay(
                frameW = layout.width,
                frameH = layout.height,
                selected = selectedId?.let { layout.frames[it] },
                ghost = ghost,
                viewport = viewport
            )
        }
    }
}

/** يحدد هدف اللمس: مقبض العنصر المحدد، أو أعلى عنصر تحت الإصبع، أو تحريك المساحة. */
private fun decideTarget(
    rects: List<Pair<String, DesignRect>>,
    freeIds: Set<String>,
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
        return GestureTarget.Move(hit, rects.first { it.first == hit }.second, hit in freeIds)
    }
    return GestureTarget.Pan
}

/** حدود الإطار وإطار التحديد والمقابض وظل السحب، تُرسم بحجم ثابت على الشاشة مهما كان التكبير. */
@Composable
private fun SelectionOverlay(
    frameW: Float,
    frameH: Float,
    selected: DesignRect?,
    ghost: DesignRect?,
    viewport: Viewport
) {
    val density = LocalDensity.current.density
    Canvas(Modifier.fillMaxSize()) {
        val frame = CanvasMath.toScreenRect(viewport, density, DesignRect(0f, 0f, frameW, frameH))
        drawRect(
            color = Color(0x33000000),
            topLeft = Offset(frame.left, frame.top),
            size = Size(frame.width, frame.height),
            style = Stroke(1.dp.toPx())
        )

        if (ghost != null) {
            val g = CanvasMath.toScreenRect(viewport, density, ghost)
            drawRect(
                color = SelectionColor.copy(alpha = 0.18f),
                topLeft = Offset(g.left, g.top),
                size = Size(g.width, g.height)
            )
            drawRect(
                color = SelectionColor,
                topLeft = Offset(g.left, g.top),
                size = Size(g.width, g.height),
                style = Stroke(2.dp.toPx())
            )
        }

        val rect = selected?.let { CanvasMath.toScreenRect(viewport, density, it) } ?: return@Canvas
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
