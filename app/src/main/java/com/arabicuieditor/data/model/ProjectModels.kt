package com.arabicuieditor.data.model

import java.util.UUID

/** إصدار مخطط بيانات المشروع الحالي. يُرفع عند أي تغيير غير متوافق في JSON. */
const val CURRENT_SCHEMA_VERSION = 1

/** ربط حدث (مثل النقر) بإجراء. */
data class EventBinding(
    val id: String,
    val trigger: String,
    val action: String,
    val params: Map<String, String> = emptyMap()
)

/** عنصر واجهة (زر، نص، ...). قد يحتوي عناصر أبناء. */
data class UiElement(
    val id: String,
    val type: String,
    val properties: Map<String, String> = emptyMap(),
    val events: List<EventBinding> = emptyList(),
    val children: List<UiElement> = emptyList()
)

data class UiScreen(
    val id: String,
    val name: String,
    val elements: List<UiElement> = emptyList()
)

/**
 * مرجع إلى مورد (صورة، خط، ...) مخزّن كملف داخل مجلد resources للمشروع.
 * [path] نسبي لمجلد resources، فلا تُضمَّن الصور داخل JSON التصميم.
 */
data class ResourceRef(
    val id: String,
    val name: String,
    val type: String,
    val path: String
)

data class Project(
    val id: String,
    val name: String,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val createdAt: Long,
    val updatedAt: Long,
    val screens: List<UiScreen> = emptyList(),
    val resources: List<ResourceRef> = emptyList()
)

fun newId(): String = UUID.randomUUID().toString()

/** ينشئ مشروعًا جديدًا بشاشة أولى فارغة. */
fun newProject(name: String, firstScreenName: String, now: Long = System.currentTimeMillis()): Project =
    Project(
        id = newId(),
        name = name,
        createdAt = now,
        updatedAt = now,
        screens = listOf(UiScreen(id = newId(), name = firstScreenName))
    )
