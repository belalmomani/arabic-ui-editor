package com.arabicuieditor.data.export

import com.arabicuieditor.data.model.ElementTree
import com.arabicuieditor.data.model.ElementTypes
import com.arabicuieditor.data.model.ImageNames
import com.arabicuieditor.data.model.Project
import com.arabicuieditor.data.model.PropertyKeys
import com.arabicuieditor.data.model.prop
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * يولّد مشروع Android كاملًا (Gradle + Compose) من المشروع الحالي، جاهزًا للرفع إلى GitHub وبنائه
 * بـ GitHub Actions (لا يحتاج gradlew؛ الـ workflow يثبّت Gradle). دالة نقية ما عدا قراءة الصور.
 *
 * ما يُصدَّر: الشاشات (كود [ComposeExporter])، التنقل بينها، نظام التصميم، الصور المستعملة.
 * ما لا يُصدَّر: المنطق (متغيرات/سكربت)، الخطوط المخصصة (تُستبدل بخط النظام).
 */
object AndroidProjectExporter {
    const val DEFAULT_PACKAGE = "com.example.myapp"
    private val PACKAGE_RE = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    private val KEYWORDS = setOf("package", "class", "object", "fun", "val", "var", "if", "else", "when", "for", "while", "in", "is", "as", "null", "true", "false", "this", "super", "return", "break", "continue", "interface", "typealias", "try", "throw", "do")

    fun isValidPackage(p: String): Boolean =
        PACKAGE_RE.matches(p) && p.split('.').none { it in KEYWORDS }

    private fun xmlEscape(s: String): String = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("\\'")
            '@' -> append("\\@")
            '?' -> append("\\?")
            '\n', '\r' -> append(' ')
            else -> append(c)
        }
    }

    /** أسماء الصور المستعملة فعلًا في عناصر «صورة» (صالحة الاسم). */
    fun usedImages(project: Project): List<String> =
        project.screens.flatMap { ElementTree.flatten(it.elements) }
            .filter { it.type == ElementTypes.IMAGE }
            .map { it.prop(PropertyKeys.IMAGE_SRC) }
            .filter { ImageNames.isValid(it) }
            .distinct()

    /**
     * كل ملفات المشروع: مسار نسبي ← محتوى. [images] اسم الملف ← بايتاته (ما لا يوجد منها يُتخطّى
     * فيظهر خطأ ترجمة واضح في R.drawable؛ لذا تُرفق قائمة الناقص في README).
     */
    fun files(project: Project, packageName: String, images: Map<String, ByteArray>): LinkedHashMap<String, ByteArray> {
        require(isValidPackage(packageName)) { "اسم حزمة غير صالح: $packageName" }
        val out = LinkedHashMap<String, ByteArray>()
        fun text(path: String, content: String) { out[path] = content.toByteArray(Charsets.UTF_8) }
        val pkgPath = packageName.replace('.', '/')
        val appName = project.name.trim().ifEmpty { "App" }
        val used = usedImages(project)
        val missing = used.filter { it !in images }

        text("settings.gradle.kts", """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "GeneratedApp"
            include(":app")
        """.trimIndent() + "\n")
        text("build.gradle.kts", """
            plugins {
                id("com.android.application") version "8.7.3" apply false
                id("org.jetbrains.kotlin.android") version "2.0.21" apply false
                id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
            }
        """.trimIndent() + "\n")
        text("gradle.properties", """
            org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
            android.useAndroidX=true
            android.nonTransitiveRClass=true
            kotlin.code.style=official
        """.trimIndent() + "\n")
        text("app/build.gradle.kts", """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
                id("org.jetbrains.kotlin.plugin.compose")
            }

            android {
                namespace = "$packageName"
                compileSdk = 35

                defaultConfig {
                    applicationId = "$packageName"
                    minSdk = 26
                    targetSdk = 35
                    versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
                    versionName = "1.0"
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                kotlinOptions {
                    jvmTarget = "17"
                }
                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                implementation("androidx.core:core-ktx:1.15.0")
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation(platform("androidx.compose:compose-bom:2024.12.01"))
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.material3:material3")
            }
        """.trimIndent() + "\n")
        text("app/src/main/AndroidManifest.xml", """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:allowBackup="false"
                    android:label="@string/app_name"
                    android:supportsRtl="true"
                    android:theme="@style/Theme.GeneratedApp">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent() + "\n")
        text("app/src/main/res/values/strings.xml",
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n    <string name=\"app_name\">${xmlEscape(appName)}</string>\n</resources>\n")
        text("app/src/main/res/values/themes.xml", """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <style name="Theme.GeneratedApp" parent="android:Theme.Material.Light.NoActionBar" />
            </resources>
        """.trimIndent() + "\n")
        text("app/src/main/java/$pkgPath/MainActivity.kt", """
            package $packageName

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.material3.MaterialTheme

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        MaterialTheme {
                            AppRoot()
                        }
                    }
                }
            }
        """.trimIndent() + "\n")
        text("app/src/main/java/$pkgPath/Screens.kt", ComposeExporter.export(project, null, packageName, customFonts = false))
        for (name in used) images[name]?.let { out["app/src/main/res/drawable/$name"] = it }
        text(".github/workflows/build-apk.yml", """
            name: Build APK

            on:
              push:
                branches: [main]
              workflow_dispatch:

            jobs:
              build:
                runs-on: ubuntu-latest
                steps:
                  - uses: actions/checkout@v4
                  - uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: "17"
                  # يثبّت Gradle مباشرة، فلا حاجة لـ gradlew.
                  - uses: gradle/actions/setup-gradle@v4
                    with:
                      gradle-version: "8.10.2"
                  - run: gradle :app:assembleDebug --stacktrace
                  - uses: actions/upload-artifact@v4
                    with:
                      name: app-debug-apk
                      path: app/build/outputs/apk/debug/app-debug.apk
                      if-no-files-found: error
        """.trimIndent() + "\n")
        text(".gitignore", "build/\n.gradle/\nlocal.properties\n.idea/\n*.iml\n")
        text("README.md", buildString {
            appendLine("# $appName")
            appendLine()
            appendLine("مشروع Android (Kotlin + Jetpack Compose) مُولَّد من «محرر الواجهات العربي».")
            appendLine()
            appendLine("## البناء بدون Android Studio")
            appendLine("1. ارفع محتويات هذا المجلد إلى مستودع GitHub جديد (فرع main).")
            appendLine("2. من تبويب Actions شغّل «Build APK» (أو يعمل تلقائيًا عند الرفع).")
            appendLine("3. نزّل ملف app-debug-apk من نتيجة التشغيل وثبّته على هاتفك.")
            appendLine()
            appendLine("## ما فيه")
            appendLine("- `Screens.kt`: كل الشاشات والتنقل (AppRoot) ونظام التصميم (AppColors/AppRadius/AppText).")
            appendLine("- `MainActivity.kt`: نقطة الدخول. الحزمة: `$packageName`.")
            appendLine("- الصور المستعملة في `app/src/main/res/drawable/`.")
            appendLine()
            appendLine("## ما لا يتضمنه")
            appendLine("- المنطق (المتغيرات، منطق الأزرار، سكربت JS): أماكنه تعليقات TODO في `Screens.kt`.")
            appendLine("- الخطوط المخصصة: استُبدلت بخط النظام. أضف ملفات الخط إلى `res/font` وعدّل الكود.")
            appendLine("- التصميم يُرسم يسار-يمين كما في المحرر (غلاف LayoutDirection.Ltr في كل شاشة).")
            if (missing.isNotEmpty()) {
                appendLine()
                appendLine("## تحذير: صور ناقصة")
                appendLine("لم تُوجد ملفات هذه الصور عند التصدير؛ أضفها إلى `res/drawable/` بالاسم نفسه وإلا فشل البناء:")
                missing.forEach { appendLine("- `$it`") }
            }
        })
        return out
    }

    /** يكتب المشروع كملف ZIP إلى [out]؛ الصور تُقرأ من [imagesDir] (قد يكون null). */
    fun writeZip(project: Project, packageName: String, imagesDir: File?, out: OutputStream) {
        val images = LinkedHashMap<String, ByteArray>()
        for (name in usedImages(project)) {
            val f = imagesDir?.let { File(it, name) }
            if (f != null && f.isFile) images[name] = f.readBytes()
        }
        val files = files(project, packageName, images)
        ZipOutputStream(out).use { zip ->
            for ((path, bytes) in files) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
