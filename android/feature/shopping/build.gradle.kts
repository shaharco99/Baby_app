plugins {
    id("oryareach.android.feature")
}

android {
    namespace = "com.oryareach.feature.shopping"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:scanner"))
    implementation(project(":core:sync"))
    implementation(libs.compose.material.icons.extended)
}
