plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "dev.rfnotebook.storage"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    api(project(":domain"))
    implementation(project(":signal-processing"))
    api("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    // Gson is already shipped transitively by MapLibre. Declare the reviewed
    // version directly because bundle import uses its bounded streaming reader.
    implementation("com.google.code.gson:gson:2.10.1")
    ksp("androidx.room:room-compiler:2.8.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
}
