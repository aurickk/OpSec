pluginManagement {
    repositories {
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"}
stonecutter {
    kotlinController = false
    centralScript = "build.gradle"
    
 create(rootProject) {
        versions("1.20.1", "1.20.2", "1.20.4", "1.20.6", "1.21.1", "1.21.4", "1.21.6", "1.21.9", "1.21.11", "26.1", "26.2")
        vcsVersion = "1.21.11"
    }
}
