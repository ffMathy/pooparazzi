plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    // R class namespace (V1_ADDITIONS §7); the applicationId stays shared with the watch for the Data Layer.
    namespace = "com.ffmathy.pooparazzi.phone"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.ffmathy.pooparazzi"
        minSdk = 30
        targetSdk = 36
        // One version base for both apps (root build script): the phone takes base * 10, the watch base * 10 + 1.
        versionCode = rootProject.extra["pooparazzi.versionBase"] as Int * 10
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
            // R8 and resource shrinking (the watch's fonts and unused Material resources stay out of the APK).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The upload key when one is configured (CI, docs/RELEASING.md); otherwise the debug key, so a local
            // release build still installs for testing.
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }
    // The in-app language (Settings › Language, and the widgets and notifications that follow it) needs every
    // language in the install, not only the device's: no per-language splits in the app bundle.
    bundle { language { enableSplit = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // The version shown in Settings › About.
        buildConfig = true
    }
    // The font licences shown in Settings › About › Font licences, packaged from the single copy in third_party/
    // (assets/fonts/*-OFL.txt).
    sourceSets.getByName("main").assets.directories.add(rootProject.file("third_party").path)
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.preview)
    debugImplementation(libs.glance.appwidget.preview)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    debugImplementation(libs.compose.ui.test.manifest)
}
