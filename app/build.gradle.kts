plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// The optional backup (docs/BACKEND.md): the Google services plugin turns app/google-services.json into resources
// (google_app_id, default_web_client_id …). Without the file the plugin stays off and the app builds and runs with
// accounts turned off; the code looks those resources up by name, so it compiles either way.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

/**
 * The Firebase project id from app/google-services.json, or null without the file. Its default Hosting site,
 * `<project_id>.web.app` (also `<project_id>.firebaseapp.com`), serves the invite and email sign-in links that open the
 * app (App Links, docs/BACKEND.md › App Links).
 */
val firebaseProjectId: String? = file("google-services.json").takeIf { it.exists() }?.let { json ->
    @Suppress("UNCHECKED_CAST")
    val info = (groovy.json.JsonSlurper().parse(json) as Map<String, Any?>)["project_info"] as Map<String, Any?>?
    (info?.get("project_id") as String?)?.trim()?.takeIf { it.matches(Regex("[a-z0-9-]+")) }
        ?: throw GradleException("app/google-services.json has no valid project_info.project_id")
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
        // The App Links' host (src/appLinks/AndroidManifest.xml, merged only with a Firebase configuration).
        manifestPlaceholders["firebaseProjectId"] = firebaseProjectId.orEmpty()
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Hermetic unit tests: Firebase never starts in them, even on a machine with app/google-services.json
        // (AppAccounts). Tests that need accounts install a fake.
        unitTests.all { it.systemProperty("pooparazzi.accounts.disabled", "true") }
    }
}

// The invite and email sign-in App Links exist only with a Firebase configuration: without one there is no site to
// open, and no intent filter is declared at all.
if (firebaseProjectId != null) {
    androidComponents {
        onVariants { variant -> variant.sources.manifests.addStaticManifestFile("src/appLinks/AndroidManifest.xml") }
    }
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
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
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
