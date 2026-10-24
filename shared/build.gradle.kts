import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * Firebase project id for the device tests against the Firestore emulator: taken from the app's
 * google-services.json so it matches the project the emulator runs (`cashi-demo` for the owner,
 * `demo-cashi` with the example file); `demo-cashi` if the file is absent.
 */
val firestoreTestProjectId: String = providers
    .fileContents(rootProject.layout.projectDirectory.file("androidApp/google-services.json"))
    .asText.orNull
    ?.let { json ->
        val root = JsonSlurper().parseText(json) as Map<*, *>
        (root["project_info"] as? Map<*, *>)?.get("project_id") as? String
    }
    ?: "demo-cashi"

kotlin {
    android {
        namespace = "cashi.payment.oways.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        // Needed for the device-test APK's own network security config (src/androidDeviceTest/res);
        // the library itself has no resources.
        androidResources {
            enable = true
        }
        withHostTest {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            instrumentationRunnerArguments["firestoreProjectId"] = firestoreTestProjectId
        }
    }

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        // JUnit Platform runs the Kotlin tests (JUnit Jupiter) and the Cucumber engine side by side.
        testRuns.named("test") {
            executionTask.configure { useJUnitPlatform() }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.firestore)
            implementation(libs.kotlinx.coroutines.play.services)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        jvmTest.dependencies {
            implementation(project.dependencies.platform(libs.junit.bom))
            implementation(libs.cucumber.java)
            implementation(libs.cucumber.junit.platform.engine)
            implementation(libs.jackson.databind)
            implementation(libs.koin.test)
            implementation(libs.junit.platform.suite)
        }
    }
}