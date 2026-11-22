package com.arabicuieditor.data

import com.arabicuieditor.data.export.ComposeExporter
import com.arabicuieditor.data.export.FlutterExporter
import com.arabicuieditor.data.export.XmlExporter
import com.arabicuieditor.data.planner.AppPlan
import com.arabicuieditor.data.planner.AppPlanner
import com.arabicuieditor.data.planner.PlanKind
import com.arabicuieditor.data.storage.ProjectJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** كل نوع تطبيق مولَّد تلقائيًا يُحفظ ويُقرأ ويُصدَّر بالصيغ الثلاث دون استثناء. */
class PlannedExportTest {
    private val text: (Int) -> String = { "t$it" }

    @Test fun everyKindRoundTripsAndExports() {
        for (k in PlanKind.values()) {
            val p = AppPlanner.build(AppPlan(k, "تطبيقي"), text)
            val back = ProjectJsonCodec.decode(ProjectJsonCodec.encode(p))
            assertEquals(k.name, p, back)
            assertTrue(k.name, ComposeExporter.export(p, null).isNotBlank())
            assertTrue(k.name, XmlExporter.export(p, null).isNotBlank())
            assertTrue(k.name, FlutterExporter.export(p, null).isNotBlank())
        }
    }

    @Test fun sameInputsGiveSameStructureButNewIds() {
        val a = AppPlanner.build(AppPlan(PlanKind.SHOP, "س"), text)
        val b = AppPlanner.build(AppPlan(PlanKind.SHOP, "س"), text)
        assertEquals(a.screens.map { it.name }, b.screens.map { it.name })
        assertTrue(a.id != b.id)
    }
}
