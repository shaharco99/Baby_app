import java.util.Properties

plugins {
    id("oryareach.android.library")
}

/**
 * Google OAuth 2.0 client id for the Calendar integration (phase 1,
 * docs/specs/03-google-calendar-integration.md).
 *
 * TODO(app owner): create an OAuth 2.0 client in Google Cloud Console — Android application
 * type, package `com.oryareach.app`, this build's release/debug SHA-1 fingerprint(s) attached —
 * then set `googleCalendarOauthClientId` in `local.properties` (or pass it as a Gradle property
 * on CI, `-PgoogleCalendarOauthClientId=...`). Until that exists this stays blank and
 * [com.oryareach.core.security.GoogleCalendarAuthManager] fails fast with a clear error instead
 * of attempting a request that could never succeed.
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
    namespace = "com.oryareach.core.security"

    defaultConfig {
        buildConfigField(
            "String",
            "GOOGLE_CALENDAR_OAUTH_CLIENT_ID",
            "\"${connectionSetting("googleCalendarOauthClientId")}\"",
        )
        // Same kind of value as GOOGLE_CALENDAR_OAUTH_CLIENT_ID above (a Google Cloud Console
        // Web-application-type OAuth client id, used as Credential Manager's "server client
        // id") but kept as its own property — "sign in with Google" and the Calendar
        // integration are unrelated features that happen to both need one of these, and
        // there's no reason forcing them to share a client id if the app owner ever wants
        // to split them. Point both at the same value in local.properties if one client
        // covers both for now.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${connectionSetting("googleWebClientId")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":core:crypto"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    implementation(libs.koin.android)
}
