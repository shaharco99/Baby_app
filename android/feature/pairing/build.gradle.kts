plugins {
    id("oryareach.android.feature")
}

android {
    namespace = "com.oryareach.feature.pairing"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:crypto"))
    implementation(project(":core:security"))
    implementation(project(":core:scanner"))
    implementation(libs.compose.material.icons.extended)
}
