pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "rf-field-notebook"
include(
    ":app-ui",
    ":domain",
    ":radio-api",
    ":radio-hackrf-native",
    ":acquisition-service",
    ":signal-processing",
    ":storage",
    ":maps",
)
