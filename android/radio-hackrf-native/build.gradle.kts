plugins { id("com.android.library") }
android {
    namespace = "dev.rfnotebook.radio.hackrf"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_static" } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
}
dependencies {
    implementation(project(":radio-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
