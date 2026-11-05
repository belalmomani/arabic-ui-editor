package com.arabicuieditor.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.data.script.Step
import com.arabicuieditor.data.script.Steps

@StringRes
internal fun stepLabel(kind: String): Int = when (kind) {
    Steps.INC -> R.string.st_inc
    Steps.DEC -> R.string.st_dec
    Steps.SET_VAR -> R.string.st_setvar
    Steps.IF -> R.string.st_if
    Steps.TEXT -> R.string.st_text
    Steps.SHOW -> R.string.st_show
    Steps.HIDE -> R.string.st_hide
    Steps.TOGGLE -> R.string.st_toggle
    Steps.COLOR -> R.string.st_color
    Steps.BG -> R.string.st_bg
    Steps.TOAST -> R.string.st_toast
    Steps.NAV -> R.string.st_nav
    else -> R.string.st_back
}

/**
 * محرر «منطق بالأزرار»: قائمة خطوات تُبنى بالضغط على شرائح (بلا كتابة كود).
 * التعديلات على مسودة محلية وتُحفظ دفعة واحدة بزر «حفظ المنطق» (خطوة تراجع واحدة).
 */
@Composable
fun BlocksEditor(
    steps: List<Step>,
    elementNames: List<String>,
    screenNames: List<String>,
    onCommit: (List<Step>) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier
) {
    var draft by remember(steps) { mutableStateOf(steps) }
    fun set(i: Int, s: Step) { draft = draft.toMutableList().also { it[i] = s } }
    Column(modifier.testTag("${tagPrefix}_blocks_editor"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.st_hint), style = MaterialTheme.typography.bodySmall)
        draft.forEachIndexed { i, s ->
            OutlinedCard(Modifier.fillMaxWidth().testTag("${tagPrefix}_step_$i")) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}. " + stringResource(stepLabel(s.kind)),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { draft = draft.toMutableList().also { l -> val t = l.removeAt(i); l.add(i - 1, t) } },
                            enabled = i > 0, modifier = Modifier.testTag("${tagPrefix}_step_up_$i")
                        ) { Text("▲") }
                        TextButton(
                            onClick = { draft = draft.toMutableList().also { l -> val t = l.removeAt(i); l.add(i + 1, t) } },
                            enabled = i < draft.size - 1, modifier = Modifier.testTag("${tagPrefix}_step_down_$i")
                        ) { Text("▼") }
                        TextButton(
                            onClick = { draft = draft.toMutableList().also { it.removeAt(i) } },
                            modifier = Modifier.testTag("${tagPrefix}_step_del_$i")
                        ) { Text("✕", color = Color(0xFFC62828)) }
                    }
                    if (Steps.usesVar(s.kind)) {
                        OutlinedTextField(
                            value = s.a, onValueChange = { set(i, s.copy(a = it.take(30))) },
                            label = { Text(stringResource(R.string.st_var)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_step_var_$i")
                        )
                        val known = Steps.varNames(draft)
                        if (known.size > 1 || (known.size == 1 && known[0] != s.a)) {
                            ChipRow {
                                known.forEach { n ->
                                    FilterChip(selected = n == s.a, onClick = { set(i, s.copy(a = n)) }, label = { Text(n) })
                                }
                            }
                        }
                    }
                    if (Steps.usesElement(s.kind)) {
                        if (elementNames.isEmpty()) {
                            Text(stringResource(R.string.st_no_names), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
                        } else ChipRow {
                            elementNames.forEach { n ->
                                FilterChip(
                                    selected = n == s.a, onClick = { set(i, s.copy(a = n)) },
                                    label = { Text(n) }, modifier = Modifier.testTag("${tagPrefix}_step_${i}_el_$n")
                                )
                            }
                        }
                    }
                    if (s.kind == Steps.NAV) {
                        ChipRow {
                            screenNames.forEach { n ->
                                FilterChip(
                                    selected = n == s.a, onClick = { set(i, s.copy(a = n)) },
                                    label = { Text(n) }, modifier = Modifier.testTag("${tagPrefix}_step_${i}_scr_$n")
                                )
                            }
                        }
                    }
                    when (s.kind) {
                        Steps.INC, Steps.DEC -> ValueField(R.string.st_amount, s.b, "${tagPrefix}_step_val_$i") { set(i, s.copy(b = it)) }
                        Steps.SET_VAR -> ValueField(R.string.st_value, s.b, "${tagPrefix}_step_val_$i") { set(i, s.copy(b = it)) }
                        Steps.TEXT -> ValueField(R.string.st_newtext, s.b, "${tagPrefix}_step_val_$i") { set(i, s.copy(b = it)) }
                        Steps.TOAST -> ValueField(R.string.st_message, s.b, "${tagPrefix}_step_val_$i") { set(i, s.copy(b = it)) }
                        Steps.IF -> {
                            val parts = s.b.split("|", limit = 2)
                            val op = parts.getOrElse(0) { "==" }
                            val value = parts.getOrElse(1) { "" }
                            ChipRow {
                                Steps.ops.forEach { o ->
                                    FilterChip(
                                        selected = o == op, onClick = { set(i, s.copy(b = "$o|$value")) },
                                        label = { Text(o) }, modifier = Modifier.testTag("${tagPrefix}_step_${i}_op_$o")
                                    )
                                }
                            }
                            ValueField(R.string.st_value, value, "${tagPrefix}_step_val_$i") { set(i, s.copy(b = "$op|$it")) }
                        }
                        Steps.COLOR, Steps.BG -> ChipRow {
                            Steps.palette.forEach { hex ->
                                val c = Color(android.graphics.Color.parseColor(hex))
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .background(c, RoundedCornerShape(6.dp))
                                        .border(
                                            BorderStroke(if (hex == s.b) 3.dp else 1.dp, if (hex == s.b) MaterialTheme.colorScheme.primary else Color.Gray),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { set(i, s.copy(b = hex)) }
                                        .testTag("${tagPrefix}_step_${i}_c_${hex.drop(1)}")
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.st_add), style = MaterialTheme.typography.labelMedium)
        ChipRow {
            Steps.kinds.forEach { k ->
                AssistChip(
                    onClick = {
                        if (draft.size < Steps.MAX_STEPS) {
                            draft = draft + Steps.newStep(
                                k, elementNames.firstOrNull().orEmpty(), screenNames.firstOrNull().orEmpty(),
                                Steps.varNames(draft).firstOrNull().orEmpty()
                            )
                        }
                    },
                    label = { Text(stringResource(stepLabel(k))) },
                    modifier = Modifier.testTag("${tagPrefix}_add_$k")
                )
            }
        }
        TextButton(
            onClick = { onCommit(draft) },
            enabled = draft != steps,
            modifier = Modifier.testTag("${tagPrefix}_blocks_save")
        ) { Text(stringResource(R.string.st_save)) }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun ValueField(@StringRes label: Int, value: String, tag: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.take(200)) },
        label = { Text(stringResource(label)) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}
