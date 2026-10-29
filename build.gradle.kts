plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.google.services) apply false
}

// Robolectric downloads its android-all runtime jars at test time; use the same Maven Central
// mirror as the build (repo1.maven.org rate-limits CI-style traffic).
subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
        systemProperty("robolectric.dependency.repo.id", "mavenCentralMirror")
        maxHeapSize = "1536m"
        jvmArgs(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
        )
    }
}

// Release version and upload signing, shared by :app and :wear (docs/RELEASING.md). Each value comes from a Gradle
// property (-P or ~/.gradle/gradle.properties) or, failing that, an environment variable; CI sets them.
fun releaseValue(property: String, env: String): String? =
    providers.gradleProperty(property).orElse(providers.environmentVariable(env)).orNull?.trim()?.takeIf { it.isNotEmpty() }

/** The version code base: the phone ships `base * 10`, the watch `base * 10 + 1` (one applicationId, two bundles). */
val versionBase: Int = releaseValue("pooparazzi.versionCode", "POOPARAZZI_VERSION_CODE")?.toInt()
    ?: libs.versions.pooparazziVersionCode.get().toInt()
require(versionBase in 1..209_999_999) { "pooparazzi.versionCode must be between 1 and 209999999, was $versionBase" }
extra["pooparazzi.versionBase"] = versionBase
extra["pooparazzi.versionName"] = releaseValue("pooparazzi.versionName", "POOPARAZZI_VERSION_NAME")
    ?: libs.versions.pooparazziVersionName.get()

// The upload key. Without any of these, release builds fall back to the debug key (installable local builds); with
// only some of them the build stops rather than produce a bundle Google Play would reject.
val uploadSigning: Map<String, String?> = mapOf(
    "storeFile" to releaseValue("pooparazzi.upload.storeFile", "POOPARAZZI_UPLOAD_STORE_FILE"),
    "storePassword" to releaseValue("pooparazzi.upload.storePassword", "POOPARAZZI_UPLOAD_STORE_PASSWORD"),
    "keyAlias" to releaseValue("pooparazzi.upload.keyAlias", "POOPARAZZI_UPLOAD_KEY_ALIAS"),
    "keyPassword" to releaseValue("pooparazzi.upload.keyPassword", "POOPARAZZI_UPLOAD_KEY_PASSWORD"),
)
val missingSigning = uploadSigning.filterValues { it == null }.keys
if (missingSigning.isNotEmpty() && missingSigning.size < uploadSigning.size) {
    throw GradleException("Upload signing is partly configured; missing: ${missingSigning.joinToString()} (docs/RELEASING.md)")
}
extra["pooparazzi.uploadSigning"] = if (missingSigning.isEmpty()) uploadSigning.mapValues { it.value!! } else null
