pluginManagement {
    repositories {
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.8.2"
}

stonecutter {
    kotlinController = false
    centralScript = "build.gradle.kts"
    
    create(rootProject) {
        versions("1.21.4", "1.21.6", "1.21.9", "1.21.11")
        vcsVersion = "1.21.11"  // Version used in git
    }
}
