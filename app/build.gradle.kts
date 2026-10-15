plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// URL Web App Google Apps Script. Diambil dari secret GAS_URL (GitHub Actions),
// atau gradle.properties (GAS_URL=...), atau placeholder.
val gasUrl: String = System.getenv("GAS_URL")?.takeIf { it.isNotBlank() }
    ?: (project.findProperty("GAS_URL") as String?)
    ?: "https://script.google.com/macros/s/GANTI_DENGAN_DEPLOYMENT_ID/exec"

android {
    namespace = "com.example.exambrowser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.exambrowser"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GAS_URL", "\"$gasUrl\"")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Ditandatangani dengan debug key agar APK langsung bisa diinstal.
            // Untuk produksi, ganti dengan keystore sendiri.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint { checkReleaseBuilds = false; abortOnError = false }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
