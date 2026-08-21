plugins {
    id("oryareach.android.feature")
}

android {
    namespace = "com.oryareach.feature.home"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:sync"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material.icons.extended)
}
