pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()      // Required for Firebase
        mavenCentral()
    }
}

rootProject.name = "FloodWatch"
include(":app")