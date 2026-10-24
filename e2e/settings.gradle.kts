// Standalone build for the Appium end-to-end tests. It is NOT included in the app's build:
// run it from the repo root with `./gradlew -p e2e test` (same Gradle wrapper, same version catalog).
rootProject.name = "cashi-e2e"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}