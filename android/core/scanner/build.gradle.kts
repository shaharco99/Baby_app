plugins {
    id("oryareach.android.library")
    id("oryareach.android.compose")
}

android {
    namespace = "com.oryareach.core.scanner"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.code.scanner)
    implementation(libs.zxing.core)
}
