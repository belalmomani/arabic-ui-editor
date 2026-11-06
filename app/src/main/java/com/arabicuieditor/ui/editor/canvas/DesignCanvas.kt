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
import com.arabicuieditor.ui.theme.Palette
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
import com.arabicuieditor.data.model.DesignSystem
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.Tokens
import com.arabicuieditor.data.model.resolved
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.boolProp
import com.arabicuieditor.data.model.intProp
import com.arabicuieditor.data.model.prop
import com.arabicuieditor.data.model.UiScreen
import kotlin.math.hypot
import kotlin.math.max

private const val HANDLE_OUTER_TOUCH_DP = 24f
private const val HANDLE_INNER_TOUCH_DP = 12f
private const val HANDLE_VISUAL_DP = 7f
private const val EDGE_HANDLE_MIN_SIDE_DP = 96f
private const val ELEMENT_HIT_SLOP_DP = 6f
private const val TOOL_OFFSET_DP = 38f
private const val TOOL_RADIUS_DP = 16f
private const val TOOL_TOUCH_DP = 22f
private const val TOOL_GAP_DP = 5f

// مساحة عمل رمادية فاتحة، وتحديد أخضر رفيع، وخطوط محاذاة خضراء فاتحة متقطعة (ألوان المحرر الموحدة).
private val CanvasBackground = Color(0xFFE4E8EB)
private val SelectionColor = Palette.Primary
private val ConstraintColor = Palette.Warning
private val GuideDash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))

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
 * - إصبعان: تكبير/تصغير وتحريك مساحة العمل (دائمًا، حتى مع وجود عنصر محدد).
 * - زرّا التدوير والتحريك الدائريان تحت العنصر المحدد: سحب الأول يدوّره بحرية، وسحب الثاني يحرّكه.
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
    onElementRepeat: ((String) -> Unit)? = null,
    designSystem: DesignSystem = DesignSystem.DEFAULT,
    snap: SnapSettings = SnapSettings(),
    multiMode: Boolean = false,
    multiIds: Set<String> = emptySet(),
    onToggleMulti: ((String) -> Unit)? = null
) {
    val latestMultiMode by rememberUpdatedState(multiMode)
    val latestToggle by rememberUpdatedState(onToggleMulti)
    val latestLayout by rememberUpdatedState(layout)
    val latestEditable by rememberUpdatedState(editable)
    val latestOnDrop by rememberUpdatedState(onElementDrop)
    var ghost by remember { mutableStateOf<DesignRect?>(null) }
    var guides by remember { mutableStateOf<List<Guide>>(emptyList()) }
    val latestSnap by rememberUpdatedState(snap)
    val elementsById = remember(screen, designSystem) {
        ElementTree.flatten(screen.elements).map { it.resolved(designSystem) }.associateBy { it.id }
    }
    val latestSelected by rememberUpdatedState(selectedId)
    val latestViewport by rememberUpdatedState(viewport)
    val latestOnViewport by rememberUpdatedState(onViewportChange)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestOnRect by rememberUpdatedState(onElementRectChange)
    val latestOnRotate by rememberUpdatedState(onElementRotate)
    val latestOnRepeat by rememberUpdatedState(onElementRepeat)
    val latestElements by rememberUpdatedState(elementsById)
    var canvasPx by remember { mutableStateOf(IntSize.Zero) }
    val latestCanvasH by rememberUpdatedState(canvasPx.height)

    // مساحة التصميم يسار-يمين دائمًا (إحداثيات مطلقة)، بغض النظر عن اتجاه التطبيق RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .clipToBounds()
                .background(CanvasBackground)
                .onSizeChanged { canvasPx = it; onSizeChange(it) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val dens = density
                        val touchSlop = viewConfiguration.touchSlop
                        var vp = latestViewport
                        val lay = latestLayout
                        val frameW = lay.width
                        val frameH = lay.height
                        val multiOn = latestMultiMode && latestToggle != null
                        // في التحديد المتعدد لا مقابض ولا سحب عناصر: النقر يبدّل التحديد والسحب يحرّك مساحة العمل.
                        val selectedBefore = if (multiOn) null else latestSelected

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
                            py = down.position.y,
                            canvasH = latestCanvasH.toFloat()
                        )
                        val multiHit = if (multiOn) (target as? GestureTarget.Move)?.id else null
                        if (!multiOn && target is GestureTarget.Move && target.id != selectedBefore) {
                            latestOnSelect(target.id)
                        }

                        // أهداف الالتقاط تُحسب مرة عند بداية اللمس (عناصر الشاشة الأخرى لا تتحرك أثناءه).
                        val snapId = when (target) {
                            is GestureTarget.Move -> if (target.free) target.id else null
                            is GestureTarget.Resize -> if (target.rotation == 0) target.id else null
                            else -> null
                        }
                        val snapTargets = if (snapId != null) SmartAlign.targetsFor(lay, snapId) else emptyList()
                        val screenRect = DesignRect(0f, 0f, frameW, frameH)

                        var totalX = 0f
                        var totalY = 0f
                        var appliedPanX = 0f
                        var appliedPanY = 0f
                        var moved = false
                        var multiTouch = false
                        var lastX = down.position.x
                        var lastY = down.position.y
                        // بلا تعديل (معاينة بحجم مختلف): كل سحب يحرّك مساحة العمل، والنقر يحدد فقط.
                        val lockedHit = when (target) {
                            is GestureTarget.Move -> latestElements[target.id]?.prop(PropertyKeys.LOCKED) == "true"
                            is GestureTarget.Resize -> latestElements[target.id]?.prop(PropertyKeys.LOCKED) == "true"
                            is GestureTarget.Rotate -> latestElements[target.id]?.prop(PropertyKeys.LOCKED) == "true"
                            else -> false
                        }
                        val dragTarget = if (latestEditable && !multiOn && !lockedHit) target else GestureTarget.Pan

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
                                            val raw = CanvasMath.resize(
                                                dragTarget.start, dragTarget.handle, ldx / pxPerDp, ldy / pxPerDp
                                            )
                                            if (dragTarget.rotation == 0) {
                                                val s = SmartAlign.snapResize(
                                                    raw, dragTarget.handle, snapTargets, screenRect,
                                                    SmartAlign.THRESHOLD_DP / vp.scale, latestSnap
                                                )
                                                guides = s.guides
                                                latestOnRect(dragTarget.id, s.rect)
                                            } else {
                                                latestOnRect(dragTarget.id, raw)
                                            }
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
                                            if (dragTarget.free) {
                                                val s = SmartAlign.snapMove(
                                                    next, snapTargets, screenRect,
                                                    SmartAlign.THRESHOLD_DP / vp.scale, latestSnap
                                                )
                                                guides = s.guides
                                                latestOnRect(dragTarget.id, s.rect)
                                            } else ghost = next
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
                        guides = emptyList()
                        // نقرة على «+» دون سحب: تكرار العنصر المحدد.
                        if (!moved && !multiTouch && target is GestureTarget.Repeat && latestEditable) {
                            latestOnRepeat?.invoke(target.id)
                        }
                        if (multiOn && !moved && !multiTouch && multiHit != null) {
                            latestToggle?.invoke(multiHit)
                        }
                        // نقرة على فراغ بلا سحب ولا إصبعين: إلغاء التحديد.
                        if (!multiOn && !moved && !multiTouch && target is GestureTarget.Pan) {
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
                    .background(Color(designSystem.color(Tokens.BACKGROUND)))
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
                                    .graphicsLayer {
                                        rotationZ = element.intProp(PropertyKeys.ROTATION).toFloat()
                                        // القلب حول المركز: لا يغيّر حدود العنصر فلا يتأثر اللمس ولا التحديد.
                                        if (element.boolProp(PropertyKeys.FLIP_X)) scaleX = -1f
                                        if (element.boolProp(PropertyKeys.FLIP_Y)) scaleY = -1f
                                    }
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
                guides = guides,
                grid = snap.grid,
                viewport = viewport,
                constraintLines = if (!multiMode && selectedId != null &&
                    layout.parentLayoutOf(selectedId) == com.arabicuieditor.data.model.ChildLayout.CONSTRAINT
                ) {
                    elementsById[selectedId]?.let {
                        ConstraintLines.compute(it, layout.frames, layout.parentContentOf(selectedId))
                    }.orEmpty()
                } else emptyList(),
                multi = if (multiMode) multiIds.mapNotNull { layout.frames[it] } else emptyList(),
                canvasH = canvasPx.height.toFloat()
            )
        }
    }
}

/** مركزا زرّي التدوير (يسارًا) والتحريك (يمينًا) بإحداثيات العنصر غير المدار؛ تحت العنصر وإن ضاق المكان فوقه. */
private fun toolCenters(sr: DesignRect, density: Float, canvasH: Float): Pair<Offset, Offset> {
    val off = TOOL_OFFSET_DP * density
    val r = TOOL_RADIUS_DP * density
    val cx = (sr.left + sr.right) / 2f
    val below = sr.bottom + off
    val y = if (canvasH > 0f && below + r + 8f * density > canvasH) sr.top - off else below
    val half = r + TOOL_GAP_DP * density
    return Offset(cx - half, y) to Offset(cx + half, y)
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
    py: Float,
    canvasH: Float = 0f
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
            val (rc, mc) = toolCenters(sr, density, canvasH)
            val dr = hypot(lx - rc.x, ly - rc.y)
            val dm = hypot(lx - mc.x, ly - mc.y)
            if (dr <= touch && dr <= dm) {
                return GestureTarget.Rotate(
                    selected.first, cx, cy, rot, CanvasMath.angleDegrees(cx, cy, px, py)
                )
            }
            if (dm <= touch) {
                return GestureTarget.Move(selected.first, selected.second, selected.first in freeIds)
            }
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
    guides: List<Guide>,
    grid: Int,
    viewport: Viewport,
    multi: List<DesignRect> = emptyList(),
    constraintLines: List<ConstraintLine> = emptyList(),
    canvasH: Float = 0f
) {
    val density = LocalDensity.current.density
    Canvas(Modifier.fillMaxSize()) {
        for (l in constraintLines) {
            val a = CanvasMath.toScreenRect(viewport, density, DesignRect(l.x1, l.y1, 0f, 0f))
            val b = CanvasMath.toScreenRect(viewport, density, DesignRect(l.x2, l.y2, 0f, 0f))
            drawLine(ConstraintColor, Offset(a.left, a.top), Offset(b.left, b.top), 2.dp.toPx())
            drawCircle(ConstraintColor, 3.dp.toPx(), Offset(b.left, b.top))
        }
        for (m in multi) {
            val r = CanvasMath.toScreenRect(viewport, density, m)
            drawRect(
                color = SelectionColor,
                topLeft = Offset(r.left, r.top),
                size = Size(r.width, r.height),
                style = Stroke(2.dp.toPx())
            )
        }
        val frame = CanvasMath.toScreenRect(viewport, density, DesignRect(0f, 0f, frameW, frameH))
        drawRect(
            color = Color(0x33000000),
            topLeft = Offset(frame.left, frame.top),
            size = Size(frame.width, frame.height),
            style = Stroke(1.dp.toPx())
        )

        // شبكة اختيارية: تُخفى إذا تقاربت خطوطها كثيرًا على الشاشة.
        if (grid > 0 && grid * density * viewport.scale >= 8f) {
            val step = grid * density * viewport.scale
            val gridColor = Color(0x22000000)
            var gx = frame.left + step
            while (gx < frame.right) {
                drawLine(gridColor, Offset(gx, frame.top), Offset(gx, frame.bottom), 1f)
                gx += step
            }
            var gy = frame.top + step
            while (gy < frame.bottom) {
                drawLine(gridColor, Offset(frame.left, gy), Offset(frame.right, gy), 1f)
                gy += step
            }
        }

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

        // الخطوط الإرشادية: وردي للالتقاط، وبنفسجي للتباعد المتساوي.
        for (gd in guides) {
            val color = if (gd.spacing) Palette.Primary else Palette.GuideGreen
            if (gd.vertical) {
                val x = CanvasMath.toScreenX(viewport, density, gd.pos)
                val y1 = CanvasMath.toScreenY(viewport, density, gd.start)
                val y2 = CanvasMath.toScreenY(viewport, density, gd.end)
                drawLine(color, Offset(x, y1), Offset(x, y2), 1.5.dp.toPx(), pathEffect = GuideDash)
            } else {
                val y = CanvasMath.toScreenY(viewport, density, gd.pos)
                val x1 = CanvasMath.toScreenX(viewport, density, gd.start)
                val x2 = CanvasMath.toScreenX(viewport, density, gd.end)
                drawLine(color, Offset(x1, y), Offset(x2, y), 1.5.dp.toPx(), pathEffect = GuideDash)
            }
        }

        val rect = selected?.let { CanvasMath.toScreenRect(viewport, density, it) } ?: return@Canvas
        val pivot = Offset((rect.left + rect.right) / 2f, (rect.top + rect.bottom) / 2f)
        rotate(rotation.toFloat(), pivot) {
            drawRect(
                color = SelectionColor,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                style = Stroke(1.5.dp.toPx())
            )
            val radius = HANDLE_VISUAL_DP.dp.toPx()
            CanvasMath.handlePositions(rect, EDGE_HANDLE_MIN_SIDE_DP * density).forEach { h ->
                val center = Offset(h.x, h.y)
                drawCircle(Color.White, radius, center)
                drawCircle(SelectionColor, radius, center, style = Stroke(1.5.dp.toPx()))
            }
            if (showTools) {
                val r = TOOL_RADIUS_DP.dp.toPx()
                val stroke = 1.8.dp.toPx()
                val (rc, mc) = toolCenters(rect, density, canvasH)
                val ink = Palette.TextPrimary
                fun disc(c: Offset) {
                    drawCircle(Color(0x22000000), r, Offset(c.x, c.y + 1.5.dp.toPx()))
                    drawCircle(Color.White, r, c)
                    drawCircle(Palette.Border, r, c, style = Stroke(1.dp.toPx()))
                }
                // زر التدوير: سهم دائري.
                disc(rc)
                val ar = r * 0.5f
                drawArc(
                    color = ink, startAngle = -50f, sweepAngle = 280f, useCenter = false,
                    topLeft = Offset(rc.x - ar, rc.y - ar), size = Size(ar * 2, ar * 2),
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                val tip = Offset(rc.x + ar * 0.64f, rc.y - ar * 0.77f)
                drawLine(ink, tip, Offset(tip.x + ar * 0.1f, tip.y - ar * 0.7f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(ink, tip, Offset(tip.x + ar * 0.75f, tip.y + ar * 0.05f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                // زر التحريك: أربعة أسهم.
                disc(mc)
                val a2 = r * 0.55f
                val h = r * 0.22f
                val cap = androidx.compose.ui.graphics.StrokeCap.Round
                drawLine(ink, Offset(mc.x - a2, mc.y), Offset(mc.x + a2, mc.y), stroke, cap)
                drawLine(ink, Offset(mc.x, mc.y - a2), Offset(mc.x, mc.y + a2), stroke, cap)
                for ((sx, sy) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) {
                    val tx = mc.x + sx * a2
                    val ty = mc.y + sy * a2
                    drawLine(ink, Offset(tx, ty), Offset(tx - sx * h + sy * h, ty - sy * h + sx * h), stroke, cap)
                    drawLine(ink, Offset(tx, ty), Offset(tx - sx * h - sy * h, ty - sy * h - sx * h), stroke, cap)
                }
            }
        }
    }
}
