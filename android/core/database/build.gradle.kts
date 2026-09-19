plugins {
    id("oryareach.android.library")
    id("oryareach.android.room")
}

android {
    namespace = "com.oryareach.core.database"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:sync"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:settings"))
    implementation(project(":core:crypto"))
    implementation(libs.kotlinx.serialization.json)

    api(libs.room.runtime)
    implementation(libs.sqlcipher)
    implementation(libs.koin.android)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
