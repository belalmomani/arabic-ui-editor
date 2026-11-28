plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.arabicuieditor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arabicuieditor"
        minSdk = 26
        targetSdk = 36
        // يزيد رقم الإصدار تلقائيًا مع كل بناء على GitHub Actions.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.99.0"
    }

    // مفتاح توقيع ثابت للنسخة التجريبية (debug) كي يُثبَّت كل APK جديد فوق السابق
    // دون حذف. هذا مفتاح تجريبي عام وليس سرًّا؛ لا تستخدمه للنشر في المتاجر.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // توقيع النشر على جوجل بلاي («مفتاح الرفع»): يُقرأ من متغيرات البيئة التي يضعها
    // workflow ‏«Release AAB» من أسرار المستودع. لا يُخزَّن أي سر داخل الكود.
    val releaseStore = System.getenv("UPLOAD_KEYSTORE_FILE")
    if (releaseStore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    lint {
        // فحص lint للإصدار لا يُشغَّل ضمن بناء AAB كي لا يوقفه تحذير غير جوهري.
        checkReleaseBuilds = false
    }

    buildTypes {
        debug {
            // معرّفات اختبار Google الرسمية: لا إعلانات حقيقية ولا نقرات على حسابك أثناء التطوير.
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "AD_BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "AD_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
        release {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-7098954256458559~5415715760"
            buildConfigField("String", "AD_BANNER_ID", "\"ca-app-pub-7098954256458559/6034149516\"")
            buildConfigField("String", "AD_INTERSTITIAL_ID", "\"ca-app-pub-7098954256458559/9490692142\"")
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric يحتاج موارد التطبيق لاختبار واجهة Compose على JVM.
            isIncludeAndroidResources = true
            // يطبع سبب فشل أي اختبار في سجل CI (الرسالة والسطر) بدل اسم الاختبار فقط.
            all {
                it.testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation("org.mozilla:rhino:1.7.15")
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
