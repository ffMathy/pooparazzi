plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.ffmathy.xaternity.wear"
    compileSdk = 37
    defaultConfig {
        // The Wearable Data Layer only pairs apps with the same applicationId on phone and watch.
        applicationId = "com.ffmathy.xaternity"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            // Drops the phone-only fonts and code that :core carries (core's notes for the app teams).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Installable from a local build; a store build replaces this with the upload key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.compose.ui.tooling)
    implementation(libs.wear.remote.interactions)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout.material3)
    implementation(libs.wear.tiles.tooling.preview)
    implementation(libs.wear.complications.data.source.ktx)
    implementation(libs.wear.ongoing)
    implementation(libs.androidx.core.ktx)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.wear.tiles.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.wear.tiles.renderer)
    testImplementation(libs.wear.protolayout.expression.pipeline)
    debugImplementation(libs.compose.ui.test.manifest)
}
