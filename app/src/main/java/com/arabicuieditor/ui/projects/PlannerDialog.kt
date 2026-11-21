package com.arabicuieditor.ui.projects

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.data.planner.AppPlan
import com.arabicuieditor.data.planner.AppPlanner
import com.arabicuieditor.data.planner.PlanColors
import com.arabicuieditor.data.planner.PlanFeature
import com.arabicuieditor.data.planner.PlanKind

/** «مخطِّط التطبيق»: نوع ← مزايا ← هوية ← خريطة الشاشات ← مشروع جاهز بلا ذكاء اصطناعي. */
@Composable
fun PlannerDialog(onCreate: (AppPlan) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var kindName by rememberSaveable { mutableStateOf(PlanKind.RESTAURANT.name) }
    var name by rememberSaveable { mutableStateOf("") }
    var featureNames by rememberSaveable { mutableStateOf(PlanKind.RESTAURANT.defaults.map { it.name }) }
    var primary by rememberSaveable { mutableStateOf(PlanColors.all[0]) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val kind = PlanKind.valueOf(kindName)
    val plan = AppPlan(kind, name, featureNames.map { PlanFeature.valueOf(it) }.toSet(), primary)
    val map = AppPlanner.screenNames(plan) { context.getString(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("planner_dialog"),
        title = { Text(stringResource(R.string.planner_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.planner_kind), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanKind.values().forEach { k ->
                        FilterChip(
                            selected = k == kind,
                            onClick = { kindName = k.name; featureNames = k.defaults.map { it.name } },
                            label = { Text(stringResource(k.labelRes)) },
                            modifier = Modifier.testTag("planner_kind_${k.name}")
                        )
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) },
                    label = { Text(stringResource(R.string.planner_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("planner_name")
                )
                TextButton(onClick = { advanced = !advanced }, modifier = Modifier.testTag("planner_advanced")) {
                    Text(stringResource(if (advanced) R.string.planner_advanced_hide else R.string.planner_advanced))
                }
                if (advanced) Text(stringResource(R.string.planner_features), style = MaterialTheme.typography.titleSmall)
                if (advanced) PlanFeature.values().forEach { f ->
                    val on = f.name in featureNames
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            featureNames = if (on) featureNames - f.name else featureNames + f.name
                        }.testTag("planner_feature_${f.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Text(stringResource(f.labelRes), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (advanced) Text(stringResource(R.string.planner_color), style = MaterialTheme.typography.titleSmall)
                if (advanced) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlanColors.all.forEach { c ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                                .border(BorderStroke(if (c == primary) 3.dp else 1.dp, if (c == primary) MaterialTheme.colorScheme.onSurface else Color.LightGray), CircleShape)
                                .clickable { primary = c }
                        )
                    }
                }
                Text(stringResource(R.string.planner_map), style = MaterialTheme.typography.titleSmall)
                Text(
                    map.joinToString("  ←  "),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("planner_map")
                )
                Text(stringResource(R.string.planner_count, map.size), style = MaterialTheme.typography.bodySmall)
                if (name.isBlank()) Text(stringResource(R.string.planner_name_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(plan); onDismiss() }, enabled = name.isNotBlank(), modifier = Modifier.testTag("planner_create")) {
                Text(stringResource(R.string.planner_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_close)) } }
    )
}
