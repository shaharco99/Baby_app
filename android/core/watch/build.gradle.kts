plugins {
    id("oryareach.android.library")
}

/**
 * The wire contract between the phone and the watch: what the phone puts on the Data Layer and
 * how the watch reads it back. Both sides depend on this one module so the keys cannot drift.
 */
android {
    namespace = "com.oryareach.core.watch"
}

dependencies {
    api(libs.play.services.wearable)
}
