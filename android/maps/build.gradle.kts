plugins { id("com.android.library") }
android { namespace = "dev.rfnotebook.maps"; compileSdk = 36; defaultConfig { minSdk = 29 } }
dependencies {
    api(project(":domain"))
    implementation(project(":storage"))
    api("org.maplibre.gl:android-sdk-opengl:13.6.1")
    testImplementation("junit:junit:4.13.2")
}
