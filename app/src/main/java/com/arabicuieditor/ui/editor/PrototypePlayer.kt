package com.arabicuieditor.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.DesignSystem
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.Tokens
import com.arabicuieditor.data.model.boolProp
import com.arabicuieditor.data.model.intProp
import com.arabicuieditor.data.model.resolved
import com.arabicuieditor.ui.editor.canvas.ElementView
import com.arabicuieditor.ui.editor.canvas.LayoutEngine
import com.arabicuieditor.ui.editor.canvas.Prototype
import com.arabicuieditor.ui.editor.canvas.PrototypeState
import kotlin.math.min

/**
 * وضع المعاينة: يعرض الشاشة كما ستظهر (بلا مقابض أو تحديد) وينفّذ إجراءات النقر،
 * فيتحول التصميم إلى نموذج تفاعلي يُجرَّب بالانتقال بين الشاشات. لا يغيّر المشروع.
 */
@Composable
fun PrototypePlayer(
    project: Project,
    startScreenId: String,
    designSystem: DesignSystem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nav by remember { mutableStateOf(PrototypeState(startScreenId)) }
    BackHandler(onBack = { if (nav.history.isEmpty()) onClose() else nav = nav.apply(ClickAction.Back, project.screens) })

    val screen = project.screens.firstOrNull { it.id == nav.current } ?: project.screens.firstOrNull()
    if (screen == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val layout = remember(screen) { LayoutEngine.layout(screen) }
    val elements = remember(screen, designSystem) {
        ElementTree.flatten(screen.elements).map { it.resolved(designSystem) }.associateBy { it.id }
    }
    val density = LocalDensity.current.density

    Column(modifier.background(Color(0xFF15171A)).testTag("proto_player")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose, modifier = Modifier.testTag("proto_close")) {
                Text(stringResource(R.string.proto_close), color = Color.White)
            }
            TextButton(
                onClick = { nav = nav.apply(ClickAction.Back, project.screens) },
                enabled = nav.history.isNotEmpty(),
                modifier = Modifier.testTag("proto_back")
            ) { Text(stringResource(R.string.proto_back), color = if (nav.history.isNotEmpty()) Color.White else Color.Gray) }
            Text(
                screen.name,
                color = Color.White,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("proto_screen")
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            val scale = min(maxWidth.value / screen.width, maxHeight.value / screen.height)
            Box(
                Modifier
                    .size((screen.width * scale).dp, (screen.height * scale).dp)
                    .clipToBounds()
                    .pointerInput(screen.id, scale) {
                        detectTapGestures { offset ->
                            val x = offset.x / density / scale
                            val y = offset.y / density / scale
                            nav = nav.apply(Prototype.actionAt(screen, layout, x, y), project.screens)
                        }
                    }
            ) {
                Box(
                    Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .size(screen.width.dp, screen.height.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .background(Color(designSystem.color(Tokens.BACKGROUND)))
                ) {
                    layout.paintOrder.forEach { id ->
                        val element = elements[id]
                        val rect = layout.frames[id]
                        if (element != null && rect != null && element.boolProp(PropertyKeys.VISIBLE)) {
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
            }
        }
    }
}
