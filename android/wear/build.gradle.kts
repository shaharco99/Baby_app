plugins {
    id("oryareach.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * The Wear OS companion: the feed and pump clocks on the wrist, read-only.
 *
 * It holds no workspace, no key and no database. The phone hands it bare timestamps over the
 * Wearable Data Layer ([com.oryareach.core.watch.WatchTimers]) and it ticks them locally.
 *
 * The Data Layer only connects apps with the same application id *and* the same signing key, so
 * this is `com.oryareach.app` too and is signed exactly like `:app` — a debug-signed watch build
 * paired with a release phone build receives nothing.
 */
val keystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")
val hasReleaseSigning = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

android {
    namespace = "com.oryareach.wear"

    defaultConfig {
        applicationId = "com.oryareach.app"
        minSdk = libs.versions.wearMinSdk.get().toInt()
    }

    buildFeatures.compose = true

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
    }
}

dependencies {
    implementation(project(":core:watch"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
}
