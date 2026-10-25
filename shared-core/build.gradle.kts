import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * The API contract and the core domain rules, shared by the clients (through `shared`) and the
 * server. Only kotlinx-serialization: no Ktor, Koin or Firebase, so none of them reach the server.
 */
kotlin {
    android {
        namespace = "cashi.payment.oways.shared.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        withHostTest {}
    }

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        testRuns.named("test") {
            executionTask.configure { useJUnitPlatform() }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(project.dependencies.platform(libs.junit.bom))
        }
    }
}