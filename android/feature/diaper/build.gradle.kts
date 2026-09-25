plugins {
    id("oryareach.android.feature")
}

android {
    namespace = "com.oryareach.feature.diaper"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:sync"))
    implementation(project(":core:domain"))
    implementation(libs.compose.material.icons.extended)
}
