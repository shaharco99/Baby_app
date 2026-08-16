plugins {
    id("oryareach.android.feature")
}

android {
    namespace = "com.oryareach.feature.settings"
}

dependencies {
    implementation(project(":core:security"))
    implementation(project(":core:settings"))
    implementation(project(":core:network"))
    implementation(project(":core:calendar"))
    implementation(libs.compose.material.icons.extended)
}
