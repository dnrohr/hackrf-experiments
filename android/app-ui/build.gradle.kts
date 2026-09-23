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
        versionCode = 5
        versionName = "1.0.0-rc1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // ADR 002 limits the MVP package to the two reviewed native ABIs.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildTypes {
        getByName("debug") {
            // Keep instrumentation installs isolated from the private-sideload
            // package. connectedAndroidTest uninstalls its target at teardown,
            // which must never erase a field notebook created by the release app.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    buildFeatures { compose = true }
}
dependencies {
    // Last stable BOM whose artifacts compile against the stable Android 16 SDK.
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation(project(":acquisition-service"))
    implementation(project(":maps"))
    implementation(project(":storage"))
    implementation(project(":radio-hackrf-native"))
    implementation(project(":radio-api"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
