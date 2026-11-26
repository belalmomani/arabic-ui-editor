package com.arabicuieditor.ui.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arabicuieditor.R
import com.arabicuieditor.data.planner.ContentSlot
import com.arabicuieditor.data.planner.ContentSlots
import com.arabicuieditor.data.planner.SlotKind
import com.arabicuieditor.ui.theme.AppButton
import com.arabicuieditor.ui.theme.AppOutlinedButton

private enum class Stage { CONTACT, TEXTS, IMAGES }

/** مراحل اختيارية: التواصل ← النصوص ← الصور. تُعرض المراحل الموجودة فعلًا في القالب فقط. */
@Composable
fun FillScreen(
    projectId: String,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    viewModel: FillViewModel = viewModel()
) {
    val slots by viewModel.slots.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val done by viewModel.doneImages.collectAsState()
    val imageError by viewModel.imageError.collectAsState()

    val stages = remember(slots) {
        buildList {
            if (slots.any { it.kind in CONTACT_KINDS }) add(Stage.CONTACT)
            if (slots.any { it.kind == SlotKind.TEXT }) add(Stage.TEXTS)
            if (slots.any { it.kind == SlotKind.IMAGE }) add(Stage.IMAGES)
        }
    }
    var index by rememberSaveable { mutableStateOf(0) }
    val values = remember { mutableStateMapOf<String, String>() }

    var pickTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val t = pickTarget
        if (uri != null && t != null) viewModel.importImage(t, uri)
        pickTarget = null
    }

    fun goEditor() { viewModel.finish(); onOpenEditor() }

    Surface(Modifier.fillMaxSize().testTag("fill_screen")) {
        Column(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.fill_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.fill_intro), style = MaterialTheme.typography.bodySmall)
            val stage = stages.getOrNull(index)
            if (!loaded) {
                Text("…")
            } else if (stage == null) {
                Text(stringResource(R.string.fill_empty))
            } else {
                Text(
                    stringResource(
                        when (stage) {
                            Stage.CONTACT -> R.string.fill_stage_contact
                            Stage.TEXTS -> R.string.fill_stage_texts
                            Stage.IMAGES -> R.string.fill_stage_images
                        }
                    ) + "  (${index + 1}/${stages.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (stage) {
                        Stage.CONTACT -> slots.filter { it.kind in CONTACT_KINDS }.forEach { s ->
                            FieldRow(s, values, label = stringResource(when (s.kind) {
                                SlotKind.EMAIL -> R.string.fill_email
                                SlotKind.WHATSAPP -> R.string.fill_whatsapp
                                SlotKind.ADDRESS -> R.string.fill_address
                                else -> R.string.fill_phone
                            }))
                        }
                        Stage.TEXTS -> slots.filter { it.kind == SlotKind.TEXT }.groupBy { it.screenName }.forEach { (screen, list) ->
                            Text(screen, style = MaterialTheme.typography.labelLarge)
                            list.forEach { s -> FieldRow(s, values, label = s.current.take(30)) }
                        }
                        Stage.IMAGES -> slots.filter { it.kind == SlotKind.IMAGE }.forEachIndexed { i, s ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.fill_image_n, i + 1, s.screenName))
                                AppOutlinedButton(
                                    onClick = { pickTarget = s.elementId; picker.launch("image/*") },
                                    modifier = Modifier.fillMaxWidth().testTag("fill_image_${i}")
                                ) { Text(stringResource(if (s.elementId in done) R.string.fill_image_done else R.string.fill_pick_image)) }
                            }
                        }
                    }
                    if (imageError) Text(stringResource(R.string.fill_image_failed), color = MaterialTheme.colorScheme.error)
                }
                val last = index == stages.lastIndex
                AppButton(
                    onClick = {
                        val mine = slots.filter { stageOf(it) == stage }.associate { it.elementId to values[it.elementId].orEmpty() }
                        viewModel.save(mine.filter { (id, v) -> v.isNotBlank() && ContentSlots.isValid(kindOf(slots, id), v) })
                        if (last) goEditor() else index++
                    },
                    modifier = Modifier.fillMaxWidth().testTag("fill_save")
                ) { Text(stringResource(if (last) R.string.fill_finish else R.string.fill_save)) }
                if (!last) AppOutlinedButton(onClick = { index++ }, modifier = Modifier.fillMaxWidth().testTag("fill_skip")) {
                    Text(stringResource(R.string.fill_skip))
                }
            }
            AppOutlinedButton(onClick = { goEditor() }, modifier = Modifier.fillMaxWidth().testTag("fill_skip_all")) {
                Text(stringResource(R.string.fill_skip_all))
            }
            TextButton(onClick = onBack, modifier = Modifier.testTag("fill_back")) { Text(stringResource(R.string.setup_back)) }
        }
    }
}

private fun stageOf(s: ContentSlot): Stage = when (s.kind) {
    SlotKind.EMAIL, SlotKind.PHONE, SlotKind.WHATSAPP, SlotKind.ADDRESS -> Stage.CONTACT
    SlotKind.TEXT -> Stage.TEXTS
    SlotKind.IMAGE -> Stage.IMAGES
}

private val CONTACT_KINDS = setOf(SlotKind.EMAIL, SlotKind.PHONE, SlotKind.WHATSAPP, SlotKind.ADDRESS)

private fun kindOf(slots: List<ContentSlot>, id: String): SlotKind = slots.first { it.elementId == id }.kind

@Composable
private fun FieldRow(s: ContentSlot, values: MutableMap<String, String>, label: String) {
    val v = values[s.elementId].orEmpty()
    val invalid = v.isNotBlank() && !ContentSlots.isValid(s.kind, v)
    OutlinedTextField(
        value = v,
        onValueChange = { values[s.elementId] = it.take(200) },
        label = { Text(label) },
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.fill_invalid)) }) else null,
        singleLine = s.kind != SlotKind.TEXT && s.kind != SlotKind.ADDRESS,
        modifier = Modifier.fillMaxWidth()
    )
}
