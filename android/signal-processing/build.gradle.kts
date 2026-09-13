plugins {
    id("com.android.library")
}
android { namespace = "dev.rfnotebook.signal.processing"; compileSdk = 36; defaultConfig { minSdk = 29 } }
dependencies { testImplementation("junit:junit:4.13.2") }
