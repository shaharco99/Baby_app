plugins {
    id("oryareach.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.oryareach.core.calendar"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(libs.room.ktx)

    // Plain REST over Ktor (already the codebase's HTTP client of choice — see
    // core/network/build.gradle.kts) rather than com.google.api-client:google-api-client-android
    // + com.google.apis:google-api-services-calendar: the official Java client pulls in its own
    // HTTP stack, GSON, and a code-generated surface far bigger than the three read-only
    // endpoints phase 1 needs (calendarList.list, events.list) — a handful of @Serializable DTOs
    // over the existing Ktor/kotlinx.serialization stack is both smaller and more consistent
    // with the rest of the app.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.koin.android)
}
