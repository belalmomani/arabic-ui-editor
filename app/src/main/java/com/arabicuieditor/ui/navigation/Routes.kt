package com.arabicuieditor.ui.navigation

/** مسارات التنقل الداخلي. تُضاف المسارات الجديدة هنا فقط. */
object Routes {
    const val HOME = "home"
    const val PROJECTS = "projects"
    const val EDITOR = "editor/{projectId}"
    const val ABOUT = "about"

    fun editor(projectId: String) = "editor/$projectId"
}
