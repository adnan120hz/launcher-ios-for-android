plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("app.cash.paparazzi") version "1.3.5"
}

android {
    namespace = "net.adnan120hz.camera26"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.adnan120hz.camera26"
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "2.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.kotlinx.coroutines.android)
    // ML Kit selfie segmentation: powers the real Portrait fallback
    // (background blur) on devices whose OEM ships no BOKEH extension.
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

    // Paparazzi: renders the real Compose UI to PNG screenshots on the JVM
    // (used to verify camera UI states without a physical device).
    testImplementation("app.cash.paparazzi:paparazzi:1.3.5")
    // Plain JUnit for the pure pixel-core proofs (PhotoConfigProcessorTest).
    testImplementation("junit:junit:4.13.2")
}
