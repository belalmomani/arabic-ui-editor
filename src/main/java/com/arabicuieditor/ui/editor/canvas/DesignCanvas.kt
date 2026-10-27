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
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.intProp
import com.arabicuieditor.data.model.UiScreen
import kotlin.math.hypot

private const val HANDLE_OUTER_TOUCH_DP = 24f
private const val HANDLE_INNER_TOUCH_DP = 12f
private const val HANDLE_VISUAL_DP = 7f
private const val EDGE_HANDLE_MIN_SIDE_DP = 96f
private const val ELEMENT_HIT_SLOP_DP = 6f
private const val TOOL_OFFSET_DP = 34f
private const val TOOL_RADIUS_DP = 13f
private const val TOOL_TOUCH_DP = 24f

private val CanvasBackground = Color(0xFF9AA3AD)
private val SelectionColor = Color(0xFF1B8CFF)

/** ماذا يفعل اللمس الحالي. يُحدَّد لحظة وضع الإصبع. */
private sealed interface GestureTarget {
    data object Pan : GestureTarget
    /** [free] صحيح إن كان العنصر حر الموضع (أبوه بتخطيط حر) فيتحرك مباشرة أثناء السحب. */
    data class Move(val id: String, val start: DesignRect, val free: Boolean) : GestureTarget
    data class Resize(val id: String, val start: DesignRect, val handle: HandleType, val rotation: Int) : GestureTarget
    /** تدوير حر: [cx],[cy] مركز العنصر على الشاشة، و[startAngle] زاوية اللمس الأولى. */
    data class Rotate(val id: String, val cx: Float, val cy: Float, val startRotation: Int, val startAngle: Float) : GestureTarget
    /** زر «+» لتكرار العنصر المحدد. */
    data class Repeat(val id: String) : GestureTarget
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
    onElementDrop: ((String, Float, Float) -> Unit)? = null,
    onElementRotate: ((String, Int) -> Unit)? = null,
    onElementRepeat: ((String) -> Unit)? = null
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
    val latestOnRotate by rememberUpdatedState(onElementRotate)
    val latestOnRepeat by rememberUpdatedState(onElementRepeat)
    val latestElements by rememberUpdatedState(elementsById)

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
                            rotationOf = { id -> latestElements[id]?.intProp(PropertyKeys.ROTATION) ?: 0 },
                            showTools = latestEditable && (latestOnRotate != null || latestOnRepeat != null),
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
                                        is GestureTarget.Resize -> {
                                            // السحب يُحوَّل إلى إحداثيات العنصر غير المدار.
                                            val (ldx, ldy) = CanvasMath.rotatePoint(
                                                totalX, totalY, 0f, 0f, -dragTarget.rotation.toFloat()
                                            )
                                            latestOnRect(
                                                dragTarget.id,
                                                CanvasMath.resize(dragTarget.start, dragTarget.handle, ldx / pxPerDp, ldy / pxPerDp)
                                            )
                                        }

                                        is GestureTarget.Rotate -> {
                                            val angle = CanvasMath.angleDegrees(dragTarget.cx, dragTarget.cy, lastX, lastY)
                                            val raw = dragTarget.startRotation + (angle - dragTarget.startAngle)
                                            latestOnRotate?.invoke(dragTarget.id, CanvasMath.snapAngle(CanvasMath.normalizeDegrees(raw)))
                                        }

                                        is GestureTarget.Repeat -> Unit

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
                        // نقرة على «+» دون سحب: تكرار العنصر المحدد.
                        if (!moved && !multiTouch && target is GestureTarget.Repeat && latestEditable) {
                            latestOnRepeat?.invoke(target.id)
                        }
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
                                    .graphicsLayer { rotationZ = element.intProp(PropertyKeys.ROTATION).toFloat() }
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
                rotation = selectedId?.let { elementsById[it]?.intProp(PropertyKeys.ROTATION) } ?: 0,
                showTools = editable && (onElementRotate != null || onElementRepeat != null),
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
    rotationOf: (String) -> Int,
    showTools: Boolean,
    vp: Viewport,
    density: Float,
    px: Float,
    py: Float
): GestureTarget {
    val selected = rects.firstOrNull { it.first == selectedId }
    if (selected != null) {
        val sr = CanvasMath.toScreenRect(vp, density, selected.second)
        val rot = rotationOf(selected.first)
        val cx = (sr.left + sr.right) / 2f
        val cy = (sr.top + sr.bottom) / 2f
        // نقطة اللمس بإحداثيات العنصر غير المدار.
        val (lx, ly) = if (rot == 0) px to py else CanvasMath.rotatePoint(px, py, cx, cy, -rot.toFloat())
        if (showTools) {
            val touch = TOOL_TOUCH_DP * density
            val off = TOOL_OFFSET_DP * density
            if (hypot(lx - cx, ly - (sr.top - off)) <= touch) {
                return GestureTarget.Rotate(
                    selected.first, cx, cy, rot, CanvasMath.angleDegrees(cx, cy, px, py)
                )
            }
            if (hypot(lx - cx, ly - (sr.bottom + off)) <= touch) return GestureTarget.Repeat(selected.first)
        }
        val handles = CanvasMath.handlePositions(sr, EDGE_HANDLE_MIN_SIDE_DP * density)
        val handle = CanvasMath.hitHandle(
            handles, sr, lx, ly,
            outerRadius = HANDLE_OUTER_TOUCH_DP * density,
            innerRadius = HANDLE_INNER_TOUCH_DP * density
        )
        if (handle != null) return GestureTarget.Resize(selected.first, selected.second, handle, rot)
    }
    val designX = CanvasMath.toDesignX(vp, density, px)
    val designY = CanvasMath.toDesignY(vp, density, py)
    val slopDesign = ELEMENT_HIT_SLOP_DP / vp.scale
    val hit = CanvasMath.hitTestRotated(rects, rotationOf, designX, designY, slopDesign)
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
    rotation: Int,
    showTools: Boolean,
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
        val pivot = Offset((rect.left + rect.right) / 2f, (rect.top + rect.bottom) / 2f)
        rotate(rotation.toFloat(), pivot) {
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
            if (showTools) {
                val off = TOOL_OFFSET_DP.dp.toPx()
                val r = TOOL_RADIUS_DP.dp.toPx()
                val stroke = 2.dp.toPx()
                // مقبض التدوير: دائرة بيضاء بسهم دائري، يصله خط بالعنصر.
                val rc = Offset(pivot.x, rect.top - off)
                drawLine(SelectionColor, Offset(pivot.x, rect.top), Offset(rc.x, rc.y + r), stroke)
                drawCircle(Color.White, r, rc)
                drawCircle(SelectionColor, r, rc, style = Stroke(stroke))
                drawArc(
                    color = SelectionColor,
                    startAngle = -60f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(rc.x - r * 0.5f, rc.y - r * 0.5f),
                    size = Size(r, r),
                    style = Stroke(stroke)
                )
                // زر التكرار «+».
                val pc = Offset(pivot.x, rect.bottom + off)
                drawLine(SelectionColor, Offset(pivot.x, rect.bottom), Offset(pc.x, pc.y - r), stroke)
                drawCircle(SelectionColor, r, pc)
                val arm = r * 0.5f
                drawLine(Color.White, Offset(pc.x - arm, pc.y), Offset(pc.x + arm, pc.y), stroke)
                drawLine(Color.White, Offset(pc.x, pc.y - arm), Offset(pc.x, pc.y + arm), stroke)
            }
        }
    }
}
