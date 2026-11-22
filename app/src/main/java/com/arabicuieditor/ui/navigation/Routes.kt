package com.arabicuieditor.ui.navigation

/** مسارات التنقل الداخلي. تُضاف المسارات الجديدة هنا فقط. */
object Routes {
    const val HOME = "home"
    const val PROJECTS = "projects"
    const val EDITOR = "editor/{projectId}"
    const val ABOUT = "about"
    const val SETUP = "setup/{projectId}"
    const val FILL = "fill/{projectId}"

    fun fill(projectId: String) = "fill/$projectId"
    fun setup(projectId: String) = "setup/$projectId"
    fun editor(projectId: String) = "editor/$projectId"
}
