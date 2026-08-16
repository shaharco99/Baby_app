plugins {
    id("oryareach.android.feature")
}

android {
    namespace = "com.oryareach.feature.calendar"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:domain"))
    implementation(project(":core:calendar"))
    implementation(project(":core:settings"))
    implementation(libs.compose.material.icons.extended)
}
