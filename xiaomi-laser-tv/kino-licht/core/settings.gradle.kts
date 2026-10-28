// Eigenständiger Build für die Kernlogik: läuft ohne Android SDK (./gradlew -p core test).
// Die App kompiliert dieselben Quellen direkt mit (siehe app/build.gradle.kts).
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kino-licht-core"
