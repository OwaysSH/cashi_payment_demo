import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.kotlinSerialization)
}

/** `local.properties` key that overrides the backend URL, e.g. `http://10.0.2.2:8080/` on an emulator. */
val apiBaseUrlProperty = "api.baseUrl"

/** Default: the backend on this machine, reached from a real device via `adb reverse`. */
val defaultApiBaseUrl = "http://127.0.0.1:8080"

val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull
        ?.let { load(it.reader()) }
}

val apiBaseUrl: String = localProperties.getProperty(apiBaseUrlProperty, defaultApiBaseUrl).also { url ->
    require(url.startsWith("http://") || url.startsWith("https://")) {
        "$apiBaseUrlProperty must start with http:// or https:// (was '$url')"
    }
}

/** `local.properties` key: `false` makes debug builds use the real Firestore project. Release never uses the emulator. */
val firestoreUseEmulatorProperty = "firestore.useEmulator"

/** `local.properties` key: emulator address as `host` or `host:port`, e.g. `10.0.2.2:8085` on an Android emulator. */
val firestoreEmulatorHostProperty = "firestore.emulatorHost"

/** Default: the Firestore emulator on this machine, reached from a real device via `adb reverse`. */
val defaultFirestoreEmulatorHost = "127.0.0.1"
val defaultFirestoreEmulatorPort = 8085
val validPorts = 1..65_535

val firestoreUseEmulatorInDebug: Boolean = localProperties.getProperty(firestoreUseEmulatorProperty, "true").let { value ->
    requireNotNull(value.toBooleanStrictOrNull()) {
        "$firestoreUseEmulatorProperty must be true or false (was '$value')"
    }
}

val (firestoreEmulatorHost, firestoreEmulatorPort) =
    localProperties.getProperty(firestoreEmulatorHostProperty, defaultFirestoreEmulatorHost).let { value ->
        val host = value.substringBefore(':')
        val port = if (':' in value) value.substringAfter(':').toIntOrNull() else defaultFirestoreEmulatorPort
        require(host.isNotBlank() && port != null && port in validPorts) {
            "$firestoreEmulatorHostProperty must be host or host:port (was '$value')"
        }
        host to port
    }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

android {
    namespace = "cashi.payment.oways"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "cashi.payment.oways"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "FIRESTORE_EMULATOR_HOST", "\"$firestoreEmulatorHost\"")
        buildConfigField("int", "FIRESTORE_EMULATOR_PORT", "$firestoreEmulatorPort")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        debug {
            buildConfigField("boolean", "USE_FIRESTORE_EMULATOR", "$firestoreUseEmulatorInDebug")
        }
        release {
            buildConfigField("boolean", "USE_FIRESTORE_EMULATOR", "false")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}