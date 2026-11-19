plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ir.taghziehoshmand.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.taghziehoshmand.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.3.0"
        resourceConfigurations += listOf("fa")
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // پایگاه داده آفلاین
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // تنظیمات کاربر
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // یادآورها و کارهای پس‌زمینه
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // بارگذاری تصاویر
    implementation("io.coil-kt:coil-compose:2.7.0")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")

    // دوربین و اسکن بارکد
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // تشخیص روی‌دستگاه غذا از عکس (ML Kit Image Labeling — آفلاین)
    implementation("com.google.mlkit:image-labeling:17.0.8")
    implementation("com.google.guava:guava:33.3.1-android")

    // همگام‌سازی با Health Connect (جایگزین مدرن Google Fit)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
