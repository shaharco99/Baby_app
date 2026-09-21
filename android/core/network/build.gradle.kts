import java.util.Properties

plugins {
    id("oryareach.android.library")
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Supabase connection details.
 *
 * Read from local.properties first, then from a Gradle property so CI can pass them with -P.
 * `providers.gradleProperty` alone does NOT see local.properties — that file is an Android
 * convention, not a Gradle one — so it has to be loaded explicitly. Done through
 * `providers.fileContents` rather than a bare file read so the configuration cache tracks it
 * and a changed value actually triggers a rebuild.
 */
val localProperties: Provider<Properties> =
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .map { text ->
            val parsed = Properties()
            parsed.load(text.reader())
            parsed
        }

fun connectionSetting(name: String): String =
    localProperties.map { it.getProperty(name).orEmpty() }
        .orElse("")
        .get()
        .ifBlank { providers.gradleProperty(name).getOrElse("") }

android {
    namespace = "com.oryareach.core.network"

    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"${connectionSetting("supabaseUrl")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${connectionSetting("supabaseAnonKey")}\"")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":core:sync"))
    implementation(project(":core:common"))
    implementation(project(":core:security"))

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.android)
}
