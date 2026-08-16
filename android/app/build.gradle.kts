plugins {
    id("oryareach.android.application")
    id("oryareach.android.compose")
}

/**
 * Release signing comes from the environment so the keystore never enters the repository.
 * CI decodes ANDROID_KEYSTORE_BASE64 into this path before building.
 *
 * The key must stay the same for the life of the app: Android refuses to install an update
 * signed by a different key, and a reinstall wipes local data. See
 * docs/architecture/011-release-signing-and-updates.md.
 */
val keystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")
val hasReleaseSigning = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

android {
    namespace = "com.oryareach.app"

    // versionName / versionCode come from the newest v* git tag, set by the convention plugin.
    defaultConfig {
        applicationId = "com.oryareach.app"
    }

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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without the secrets present (local builds, forks) this stays unsigned rather than
            // silently falling back to the debug key, which would produce an uninstallable update.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:crypto"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":core:sync"))
    implementation(project(":core:ui"))
    implementation(project(":core:update"))
    implementation(project(":core:settings"))
    implementation(project(":core:calendar"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:search"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:conflicts"))
    implementation(project(":feature:pairing"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:cycle"))
    implementation(project(":feature:update"))
    implementation(project(":feature:shopping"))
    implementation(project(":feature:dates"))
    implementation(project(":feature:home"))
    implementation(project(":feature:folders"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.compose.material.icons.extended)
}
