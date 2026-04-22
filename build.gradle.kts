plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace  = "com.ringhud"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ringhud"
        minSdk        = 26
        targetSdk     = 34
        versionCode   = 1
        versionName   = "1.0"

        // Read Claude API key from local.properties  →  put  claudeApiKey=sk-ant-…  there
        val claudeKey = project.findProperty("claudeApiKey")?.toString() ?: ""
        buildConfigField("String", "CLAUDE_API_KEY", "\"$claudeKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose     = true
        buildConfig = true
    }
}

dependencies {
    val composeBom      = "2024.05.00"
    val cameraxVersion  = "1.3.3"
    val hiltVersion     = "2.51.1"
    val roomVersion     = "2.6.1"
    val coroutines      = "1.8.1"
    val lifecycleVer    = "2.8.1"

    // ── Compose ──────────────────────────────────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:$composeBom"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVer")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVer")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── CameraX ──────────────────────────────────────────────────────────────
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ── Hilt ─────────────────────────────────────────────────────────────────
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-android-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ── Room ─────────────────────────────────────────────────────────────────
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines")

    // ── Networking (Claude API) ───────────────────────────────────────────────
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    // ── Accompanist permissions ───────────────────────────────────────────────
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // ── Lifecycle / Core ─────────────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-service:$lifecycleVer")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVer")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
