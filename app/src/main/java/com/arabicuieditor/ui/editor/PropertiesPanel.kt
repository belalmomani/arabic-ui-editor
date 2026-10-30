package com.arabicuieditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicuieditor.R
import com.arabicuieditor.data.model.Axis
import com.arabicuieditor.data.model.ChildLayout
import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ColorUtil
import com.arabicuieditor.data.model.Interactions
import com.arabicuieditor.data.model.UiScreen
import com.arabicuieditor.data.model.Contrast
import com.arabicuieditor.data.model.resolved
import com.arabicuieditor.data.model.colorProp
import com.arabicuieditor.data.model.intProp
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.PropertyContext
import com.arabicuieditor.data.model.PropertyDef
import com.arabicuieditor.data.model.PropertyGroup
import com.arabicuieditor.data.model.PropertyKind
import com.arabicuieditor.data.model.PropertyRegistry
import com.arabicuieditor.data.model.Tokens
import com.arabicuieditor.data.model.SizeMode
import com.arabicuieditor.data.model.UiElement
import com.arabicuieditor.data.model.spec
import com.arabicuieditor.data.model.prop
import com.arabicuieditor.ui.editor.canvas.AlignEdge
import com.arabicuieditor.ui.editor.canvas.DesignRect

/** ألوان جاهزة للاختيار السريع (بصيغة #AARRGGBB). غير محصورة: يمكن إدخال أي لون سداسي. */
private val Swatches = listOf(
    "#00000000", "#FF000000", "#FFFFFFFF", "#FFF2F4F7", "#FFB0B7BF", "#FF5F6B76",
    "#FF1B5E8C", "#FF5B9BD5", "#FFB3261E", "#FFE8710A", "#FFF9AB00", "#FF188038",
    "#FF12B5CB", "#FF7B1FA2", "#FFD81B60"
)

/**
 * لوحة الخصائص: تُبنى كلها من [PropertyRegistry] حسب نوع العنصر المحدد وسياقه (تخطيطه وتخطيط أبيه)،
 * فلا يوجد حقل مكتوب يدويًا لخاصية معينة. الاستثناء: أقسام الأبعاد (نمط الأبعاد، x, y, العرض, الارتفاع)
 * وطريقة ترتيب الأبناء وعمليات التغليف، لأنها من تخطيط العنصر لا من خصائصه.
 *
 * [rect] إطار العنصر نسبةً إلى أبيه (x, y محليان). حقلا x وy يظهران فقط للعنصر الحر.
 */
@Composable
fun PropertiesPanel(
    element: UiElement?,
    rect: DesignRect?,
    onSetProperty: (key: String, value: String) -> Unit,
    onSetRect: (DesignRect) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onReorder: (ReorderOp) -> Unit,
    modifier: Modifier = Modifier,
    parentLayout: ChildLayout = ChildLayout.ABSOLUTE,
    onSetSizeMode: (Axis, SizeMode) -> Unit = { _, _ -> },
    onSetPercent: (Axis, Int) -> Unit = { _, _ -> },
    onSetChildLayout: (ChildLayout) -> Unit = {},
    onWrap: (ChildLayout) -> Unit = {},
    onUnwrap: () -> Unit = {},
    onAlign: (AlignEdge) -> Unit = {},
    onDistribute: (horizontal: Boolean) -> Unit = {},
    screens: List<UiScreen> = emptyList(),
    onSetClick: (ClickAction) -> Unit = {}
) {
    if (element == null || rect == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.props_select_hint),
                style = MaterialTheme.typography.bodySmall
            )
        }
        return
    }

    val defs = PropertyRegistry.forContext(element.type, PropertyContext(element.childLayout, parentLayout))
    val isContainer = ElementTree.canContain(element.type)
    val isFree = parentLayout == ChildLayout.ABSOLUTE

    LazyColumn(modifier = modifier.testTag("props_list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "actions") {
            ActionsRow(onDuplicate, onDelete, onReorder, onWrap, onUnwrap, canUnwrap = element.children.isNotEmpty())
        }

        if (isFree) {
            item(key = "align") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(stringResource(R.string.group_align))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionChip(stringResource(R.string.align_to_left), "align_left") { onAlign(AlignEdge.LEFT) }
                        ActionChip(stringResource(R.string.align_to_center_h), "align_center_h") { onAlign(AlignEdge.CENTER_H) }
                        ActionChip(stringResource(R.string.align_to_right), "align_right") { onAlign(AlignEdge.RIGHT) }
                        ActionChip(stringResource(R.string.align_to_top), "align_top") { onAlign(AlignEdge.TOP) }
                        ActionChip(stringResource(R.string.align_to_center_v), "align_center_v") { onAlign(AlignEdge.CENTER_V) }
                        ActionChip(stringResource(R.string.align_to_bottom), "align_bottom") { onAlign(AlignEdge.BOTTOM) }
                        ActionChip(stringResource(R.string.distribute_h), "distribute_h") { onDistribute(true) }
                        ActionChip(stringResource(R.string.distribute_v), "distribute_v") { onDistribute(false) }
                    }
                }
            }
        }

        if (defs.any { it.key == PropertyKeys.TEXT_COLOR }) {
            item(key = "contrast") {
                ContrastCheck(element)
            }
        }

        item(key = "interaction") {
            InteractionSection(element, screens, onSetClick)
        }

        item(key = "sizes") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader(stringResource(R.string.group_size_mode))
                SizeModeRow(stringResource(R.string.geo_width), "w", element.layout.spec(Axis.HORIZONTAL).mode,
                    element.layout.spec(Axis.HORIZONTAL).value.toInt(),
                    { onSetSizeMode(Axis.HORIZONTAL, it) }, { onSetPercent(Axis.HORIZONTAL, it) })
                SizeModeRow(stringResource(R.string.geo_height), "h", element.layout.spec(Axis.VERTICAL).mode,
                    element.layout.spec(Axis.VERTICAL).value.toInt(),
                    { onSetSizeMode(Axis.VERTICAL, it) }, { onSetPercent(Axis.VERTICAL, it) })
            }
        }

        item(key = "geometry") {
          Column {
            SectionHeader(stringResource(R.string.group_geometry))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isFree) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(stringResource(R.string.geo_x), rect.x.toInt(), -2000, 4000,
                            { onSetRect(rect.copy(x = it.toFloat())) }, Modifier.weight(1f), "geo_x")
                        NumberField(stringResource(R.string.geo_y), rect.y.toInt(), -2000, 4000,
                            { onSetRect(rect.copy(y = it.toFloat())) }, Modifier.weight(1f), "geo_y")
                    }
                } else {
                    Text(
                        text = stringResource(R.string.geo_flow_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(stringResource(R.string.geo_width), rect.w.toInt(), 16, 2000,
                        { onSetRect(rect.copy(w = it.toFloat())) }, Modifier.weight(1f), "geo_w")
                    NumberField(stringResource(R.string.geo_height), rect.h.toInt(), 16, 2000,
                        { onSetRect(rect.copy(h = it.toFloat())) }, Modifier.weight(1f), "geo_h")
                }
            }
          }
        }

        PropertyGroup.entries.forEach { group ->
            val groupDefs = defs.filter { it.group == group }
            val hasKindChooser = group == PropertyGroup.LAYOUT && isContainer
            if (groupDefs.isNotEmpty() || hasKindChooser) {
                item(key = "group_${group.name}") {
                  Column {
                    SectionHeader(stringResource(group.labelRes))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasKindChooser) {
                            ChildLayoutChooser(element.childLayout, onSetChildLayout)
                        }
                        if (group == PropertyGroup.SPACING) {
                            // الهوامش والمسافات الداخلية: حقلان في كل صف.
                            groupDefs.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { def ->
                                        PropertyEditor(def, element, onSetProperty, Modifier.weight(1f))
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            Text(
                                text = stringResource(R.string.margin_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            groupDefs.forEach { def ->
                                PropertyEditor(def, element, onSetProperty, Modifier.fillMaxWidth())
                            }
                        }
                    }
                  }
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun ActionsRow(
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onReorder: (ReorderOp) -> Unit,
    onWrap: (ChildLayout) -> Unit,
    onUnwrap: () -> Unit,
    canUnwrap: Boolean
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionChip(stringResource(R.string.action_duplicate_element), "act_duplicate", onDuplicate)
        ActionChip(stringResource(R.string.action_delete), "act_delete", onDelete)
        ActionChip(stringResource(R.string.action_forward), "act_forward") { onReorder(ReorderOp.FORWARD) }
        ActionChip(stringResource(R.string.action_backward), "act_backward") { onReorder(ReorderOp.BACKWARD) }
        ActionChip(stringResource(R.string.action_to_front), "act_front") { onReorder(ReorderOp.TO_FRONT) }
        ActionChip(stringResource(R.string.action_to_back), "act_back") { onReorder(ReorderOp.TO_BACK) }
        ActionChip(stringResource(R.string.action_wrap_row), "act_wrap_row") { onWrap(ChildLayout.ROW) }
        ActionChip(stringResource(R.string.action_wrap_column), "act_wrap_column") { onWrap(ChildLayout.COLUMN) }
        ActionChip(stringResource(R.string.action_wrap_box), "act_wrap_box") { onWrap(ChildLayout.BOX) }
        if (canUnwrap) ActionChip(stringResource(R.string.action_unwrap), "act_unwrap", onUnwrap)
    }
}

/** اختيار نمط بُعد واحد (ثابت، ملء الأب، حسب المحتوى، نسبة) وقيمة النسبة عند اختيارها. */
@Composable
private fun SizeModeRow(
    label: String,
    tagSuffix: String,
    mode: SizeMode,
    value: Int,
    onMode: (SizeMode) -> Unit,
    onPercent: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SizeModes.forEach { (m, labelRes) ->
                FilterChip(
                    selected = m == mode,
                    onClick = { onMode(m) },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.testTag("size_${tagSuffix}_${m.name}")
                )
            }
        }
        if (mode == SizeMode.FRACTION) {
            NumberField(
                stringResource(R.string.size_percent), value.coerceIn(1, 100), 1, 100,
                onPercent, Modifier.fillMaxWidth(), "size_${tagSuffix}_percent"
            )
        }
    }
}

/** اختيار طريقة ترتيب أبناء الحاوية: حر، عمود، صف، صندوق. */
@Composable
private fun ChildLayoutChooser(current: ChildLayout, onChange: (ChildLayout) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.prop_child_layout),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChildLayoutOptions.forEach { (layout, labelRes) ->
                FilterChip(
                    selected = layout == current,
                    onClick = { onChange(layout) },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.testTag("layout_${layout.name}")
                )
            }
        }
    }
}

private val SizeModes = listOf(
    SizeMode.FIXED to R.string.size_fixed,
    SizeMode.MATCH_PARENT to R.string.size_match,
    SizeMode.WRAP_CONTENT to R.string.size_wrap,
    SizeMode.FRACTION to R.string.size_fraction
)

/** خيارات تخطيط الحاوية (تُستعمل أيضًا في لوحة الشاشة). */
internal val ChildLayoutOptions = listOf(
    ChildLayout.ABSOLUTE to R.string.layout_free,
    ChildLayout.COLUMN to R.string.layout_column,
    ChildLayout.ROW to R.string.layout_row,
    ChildLayout.BOX to R.string.layout_box
)

@Composable
private fun ActionChip(label: String, tag: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.testTag(tag)
    )
}

/** يختار أداة التحرير المناسبة لنوع الخاصية لعنصر. */
@Composable
private fun PropertyEditor(
    def: PropertyDef,
    element: UiElement,
    onSet: (String, String) -> Unit,
    modifier: Modifier
) = PropertyEditorRow(def, element.prop(def.key), onSet, modifier)

/**
 * أداة تحرير خاصية بقيمتها الحالية [current] نصًّا (تُستعمل للعناصر ولجذر الشاشة معًا).
 * وسوم الاختبار: prop_<key> وملحقاتها.
 */
@Composable
internal fun PropertyEditorRow(
    def: PropertyDef,
    current: String,
    onSet: (String, String) -> Unit,
    modifier: Modifier
) {
    val label = stringResource(def.labelRes)
    val tag = "prop_${def.key}"
    val ds = LocalDesignSystem.current
    when (val kind = def.kind) {
        is PropertyKind.Text -> TextPropertyField(label, current, kind.multiline, { onSet(def.key, it) }, modifier, tag)
        is PropertyKind.Integer -> {
            val family = Tokens.intFamilyOf(def.key)
            val shown = current.toIntOrNull() ?: Tokens.resolveInt(current, ds) ?: kind.min
            Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NumberField(
                    label, shown, kind.min, kind.max,
                    { onSet(def.key, it.toString()) }, Modifier, tag
                )
                if (family != null) {
                    val names = if (family == Tokens.RADIUS) Tokens.radiusNames else Tokens.textNames
                    TokenChips(
                        refs = names.map { n ->
                            (if (family == Tokens.RADIUS) Tokens.radius(n) else Tokens.text(n)) to
                                (if (family == Tokens.RADIUS) Tokens.radiusLabel(n) else Tokens.textLabel(n))
                        },
                        current = current,
                        onPick = { onSet(def.key, it) },
                        tag = tag,
                        swatch = null
                    )
                }
            }
        }
        PropertyKind.ColorValue -> {
            val shown = if (Tokens.isToken(current)) {
                Tokens.resolveColor(current, ds)?.let { ColorUtil.format(it) } ?: current
            } else current
            Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ColorField(label, shown, { onSet(def.key, it) }, Modifier, tag)
                TokenChips(
                    refs = Tokens.colorNames.map { Tokens.color(it) to Tokens.colorLabel(it) },
                    current = current,
                    onPick = { onSet(def.key, it) },
                    tag = tag,
                    swatch = { ref -> Tokens.resolveColor(ref, ds) }
                )
            }
        }
        is PropertyKind.Choice -> ChoiceField(label, current, kind, { onSet(def.key, it) }, modifier, tag)
        PropertyKind.Toggle -> ToggleField(label, current != "false", { onSet(def.key, it.toString()) }, modifier, tag)
    }
}

/** رقاقات «ربط بنظام التصميم»: اختيار مرجع بدل قيمة ثابتة. المحدد هو المرجع الحالي للخاصية. */
@Composable
internal fun TokenChips(
    refs: List<Pair<String, Int>>,
    current: String,
    onPick: (String) -> Unit,
    tag: String,
    swatch: ((String) -> Int?)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.link_to_system),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            refs.forEach { (ref, labelRes) ->
                FilterChip(
                    selected = current == ref,
                    onClick = { onPick(ref) },
                    label = { Text(stringResource(labelRes), maxLines = 1) },
                    leadingIcon = swatch?.let { f ->
                        {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(f(ref) ?: 0))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                        }
                    },
                    modifier = Modifier.testTag("${tag}_tok_${ref.substringAfter('/')}")
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// أدوات التحرير. كلها تحتفظ بنص محلي أثناء الكتابة وتتزامن مع القيمة الحقيقية عند تغيّرها
// من الخارج (مثل التراجع)، فلا يقفز المؤشر أثناء الكتابة.
// ---------------------------------------------------------------------------------------------

@Composable
private fun TextPropertyField(
    label: String,
    value: String,
    multiline: Boolean,
    onValue: (String) -> Unit,
    modifier: Modifier,
    tag: String
) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != text) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValue(it)
        },
        label = { Text(label) },
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        modifier = modifier.testTag(tag)
    )
}

@Composable
internal fun NumberField(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onValue: (Int) -> Unit,
    modifier: Modifier,
    tag: String
) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { if (text.toIntOrNull() != value) text = value.toString() }
    val parsed = text.toIntOrNull()
    val invalid = parsed == null || parsed !in min..max
    val outline = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StepButton("−", "${tag}_dec") { onValue((value - 1).coerceIn(min, max)) }
            BasicTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    val n = input.toIntOrNull()
                    if (n != null && n in min..max) onValue(n)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { inner() } },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .border(1.dp, outline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .testTag(tag)
            )
            StepButton("+", "${tag}_inc") { onValue((value + 1).coerceIn(min, max)) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 18.sp)
    }
}

@Composable
internal fun ColorField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier,
    tag: String
) {
    val currentArgb = ColorUtil.parse(value)
    var hex by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (ColorUtil.parse(hex) != currentArgb) hex = value }
    val hexInvalid = ColorUtil.parse(hex) == null

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Swatches.forEach { swatch ->
                val argb = ColorUtil.parse(swatch)
                val selected = argb != null && argb == currentArgb
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(argb ?: 0))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { onValue(swatch) }
                        .testTag("${tag}_$swatch"),
                    contentAlignment = Alignment.Center
                ) {
                    if (swatch == "#00000000") {
                        // الشفاف: علامة «لا لون».
                        Text("∅", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(currentArgb ?: 0))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            BasicTextField(
                value = hex,
                onValueChange = { input ->
                    hex = input
                    ColorUtil.parse(input)?.let { onValue(ColorUtil.format(it)) }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { inner() } },
                modifier = Modifier
                    .width(130.dp)
                    .height(36.dp)
                    .border(
                        1.dp,
                        if (hexInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("${tag}_hex")
            )
            Text("#RRGGBB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChoiceField(
    label: String,
    value: String,
    kind: PropertyKind.Choice,
    onValue: (String) -> Unit,
    modifier: Modifier,
    tag: String
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            kind.options.forEach { (optionValue, labelRes) ->
                FilterChip(
                    selected = optionValue == value,
                    onClick = { onValue(optionValue) },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.testTag("${tag}_$optionValue")
                )
            }
        }
    }
}

@Composable
private fun ToggleField(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier,
    tag: String
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChecked, modifier = Modifier.testTag(tag))
    }
}

/** فحص تباين لون النص مع خلفية العنصر (أو خلفية الشاشة إن كانت خلفيته شفافة). */
@Composable
private fun ContrastCheck(element: UiElement) {
    val ds = LocalDesignSystem.current
    // الرموز تُحلّ مقابل نظام التصميم الفعلي (ومنه الوضع الليلي) لا الافتراضي.
    val shown = element.resolved(ds)
    val fg = shown.colorProp(PropertyKeys.TEXT_COLOR)
    val own = shown.colorProp(PropertyKeys.BG_COLOR)
    val backdrop = Contrast.over(own, ds.color(Tokens.BACKGROUND))
    val ratio = Contrast.ratio(fg, backdrop)
    val large = Contrast.isLargeText(shown.intProp(PropertyKeys.FONT_SIZE))
    val level = Contrast.level(ratio)
    val ok = level == Contrast.Level.AAA || level == Contrast.Level.AA ||
        (large && level == Contrast.Level.AA_LARGE_ONLY)
    val messageRes = when {
        level == Contrast.Level.AAA -> R.string.contrast_aaa
        level == Contrast.Level.AA -> R.string.contrast_aa
        ok -> R.string.contrast_large_ok
        level == Contrast.Level.AA_LARGE_ONLY -> R.string.contrast_large_only
        else -> R.string.contrast_fail
    }
    val tint = if (ok) Color(0xFF2E7D32) else Color(0xFFC62828)
    Column(
        modifier = Modifier.testTag("contrast_check"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SectionHeader(stringResource(R.string.contrast_title))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(width = 56.dp, height = 28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(backdrop))
                    .border(1.dp, Color(0xFFB0B7BF), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) { Text("Aa", color = Color(fg), fontSize = 14.sp) }
            Text(
                text = stringResource(R.string.contrast_ratio, ratio),
                color = tint,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("contrast_ratio")
            )
        }
        Text(stringResource(messageRes), color = tint, style = MaterialTheme.typography.bodySmall)
    }
}

/** «عند النقر»: بلا إجراء، أو انتقال إلى شاشة، أو رجوع. يعمل في وضع المعاينة (زر «تشغيل»). */
@Composable
private fun InteractionSection(element: UiElement, screens: List<UiScreen>, onSetClick: (ClickAction) -> Unit) {
    val action = Interactions.clickAction(element)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("interaction")) {
        SectionHeader(stringResource(R.string.ia_title))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = action == ClickAction.None,
                onClick = { onSetClick(ClickAction.None) },
                label = { Text(stringResource(R.string.ia_none)) },
                modifier = Modifier.testTag("click_none")
            )
            FilterChip(
                selected = action is ClickAction.Navigate,
                onClick = {
                    if (action !is ClickAction.Navigate) {
                        onSetClick(ClickAction.Navigate(screens.firstOrNull()?.id.orEmpty()))
                    }
                },
                enabled = screens.isNotEmpty(),
                label = { Text(stringResource(R.string.ia_navigate)) },
                modifier = Modifier.testTag("click_nav")
            )
            FilterChip(
                selected = action == ClickAction.Back,
                onClick = { onSetClick(ClickAction.Back) },
                label = { Text(stringResource(R.string.ia_back)) },
                modifier = Modifier.testTag("click_back")
            )
        }
        if (action is ClickAction.Navigate) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                screens.forEach { s ->
                    FilterChip(
                        selected = s.id == action.screenId,
                        onClick = { onSetClick(ClickAction.Navigate(s.id)) },
                        label = { Text(s.name) },
                        modifier = Modifier.testTag("click_to_${s.id}")
                    )
                }
            }
            if (!Interactions.isTargetValid(screens, action)) {
                Text(
                    stringResource(R.string.ia_target_missing),
                    color = Color(0xFFC62828),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("click_missing")
                )
            }
        }
    }
}
