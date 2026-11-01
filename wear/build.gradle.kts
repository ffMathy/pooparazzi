plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.ffmathy.pooparazzi.wear"
    compileSdk = 37
    defaultConfig {
        // The Wearable Data Layer only pairs apps with the same applicationId on phone and watch.
        applicationId = "com.ffmathy.pooparazzi"
        minSdk = 30
        targetSdk = 36
        // Same applicationId as the phone app: Google Play needs a version code of its own for every bundle under it,
        // so from one shared base (root build script) the phone takes base * 10 and the watch base * 10 + 1.
        versionCode = rootProject.extra["pooparazzi.versionBase"] as Int * 10 + 1
        versionName = rootProject.extra["pooparazzi.versionName"] as String
    }
    signingConfigs {
        // The upload key from the root build script, when configured (CI); docs/RELEASING.md.
        @Suppress("UNCHECKED_CAST")
        (rootProject.extra["pooparazzi.uploadSigning"] as Map<String, String>?)?.let { upload ->
            create("upload") {
                storeFile = rootProject.file(upload.getValue("storeFile"))
                storePassword = upload.getValue("storePassword")
                keyAlias = upload.getValue("keyAlias")
                keyPassword = upload.getValue("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            // Drops the phone-only fonts and code that :core carries (core's notes for the app teams).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // The upload key when one is configured (CI, docs/RELEASING.md); otherwise the debug key, so a local
            // release build still installs for testing.
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
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
    // Always-on (ambient) on Wear OS 3–5 watches, which have no Wear SDK ambient service.
    implementation(libs.wear.ambient)
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
