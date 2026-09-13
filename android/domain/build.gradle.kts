plugins { id("com.android.library") }
android { namespace = "dev.rfnotebook.domain"; compileSdk = 36; defaultConfig { minSdk = 29 } }
dependencies { testImplementation("junit:junit:4.13.2") }
