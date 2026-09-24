plugins {
    id("oryareach.android.library")
    id("oryareach.android.compose")
}

android {
    namespace = "com.oryareach.core.ui"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(libs.compose.material.icons.extended)
}
