package com.arabicuieditor.core

import android.content.Context
import com.arabicuieditor.data.storage.ProjectRepository
import com.arabicuieditor.data.storage.UserLibraryRepository
import java.io.File

/**
 * حاوية بسيطة لخدمات التطبيق.
 *
 * تحتوي الآن مستودع المشاريع المحلي فقط. عند إضافة Start.io أو Firebase أو Supabase
 * لاحقًا تُعرَّف واجهاتها في هذه الحزمة وتُنشأ هنا، دون المساس ببقية التطبيق.
 */
class AppContainer(context: Context) {
    val projects: ProjectRepository = ProjectRepository(File(context.filesDir, "projects"))
    val library: UserLibraryRepository = UserLibraryRepository(File(context.filesDir, "library/library.json"))
}
