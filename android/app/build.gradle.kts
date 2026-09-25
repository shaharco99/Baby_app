import java.util.Properties

plugins {
    id("oryareach.android.application")
    id("oryareach.android.compose")
}

/**
 * Firebase Cloud Messaging, used only to wake this device when the partner's phone writes
 * something. Configured here rather than through `google-services.json` and its Gradle plugin:
 * the plugin fails the build outright when the file is missing, and the file would have to
 * either live in the repository or be a second CI secret with its own decode step.
 *
 * Read the same way as the Supabase connection details in `:core:network` — local.properties
 * first, then a Gradle property so CI can pass it with -P. Leave them unset and push is simply
 * off: the app builds, syncs and reminds exactly as it did before, it just waits for the next
 * foreground poll instead of being woken. See PushMessagingService.
 */
val localProperties: Provider<Properties> =
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .map { text ->
            val parsed = Properties()
            parsed.load(text.reader())
            parsed
        }

fun pushSetting(name: String): String =
    localProperties.map { it.getProperty(name).orEmpty() }
        .orElse("")
        .get()
        .ifBlank { providers.gradleProperty(name).getOrElse("") }

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

        // The four values `google-services.json` would otherwise carry. None of them is a
        // secret — they identify the Firebase project to the client; the credential that can
        // actually send a message lives only in the edge function.
        buildConfigField("String", "FCM_PROJECT_ID", "\"${pushSetting("fcmProjectId")}\"")
        buildConfigField("String", "FCM_APPLICATION_ID", "\"${pushSetting("fcmApplicationId")}\"")
        buildConfigField("String", "FCM_API_KEY", "\"${pushSetting("fcmApiKey")}\"")
        buildConfigField("String", "FCM_SENDER_ID", "\"${pushSetting("fcmSenderId")}\"")
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
    implementation(project(":feature:feeding"))
    implementation(project(":feature:pumping"))
    implementation(project(":feature:diaper"))
    implementation(project(":feature:update"))
    implementation(project(":feature:shopping"))
    implementation(project(":feature:home"))
    implementation(project(":feature:folders"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

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
