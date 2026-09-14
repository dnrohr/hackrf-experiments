plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.rfnotebook.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.rfnotebook"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-m0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
}
dependencies {
    // Last stable BOM whose artifacts compile against the stable Android 16 SDK.
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation(project(":acquisition-service"))
    implementation(project(":maps"))
    implementation(project(":storage"))
    implementation(project(":radio-hackrf-native"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
