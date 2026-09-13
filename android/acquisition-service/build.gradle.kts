plugins { id("com.android.library") }
android { namespace = "dev.rfnotebook.acquisition"; compileSdk = 36; defaultConfig { minSdk = 29 } }
dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(project(":radio-api"))
    implementation(project(":radio-hackrf-native"))
    implementation(project(":signal-processing"))
}
