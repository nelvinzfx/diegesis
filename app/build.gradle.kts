import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.diegesis.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.diegesis.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.0"
    }

    // Pinned debug keystore so every CI build signs identically and
    // updates install in place (no uninstall, no data loss).
    //
    // Release signing reads from env vars set by the release workflow
    // (RELEASE_KEYSTORE_PATH / RELEASE_KEYSTORE_PASSWORD / RELEASE_KEY_ALIAS /
    // RELEASE_KEY_PASSWORD). When RELEASE_KEYSTORE_PASSWORD is absent (local
    // builds, compile-check CI) the release buildType falls back to the pinned
    // debug signing config so :app:assembleRelease still succeeds.
    val releaseKeystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")

    signingConfigs {
        named("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "diegesis-debug"
            keyPassword = "android"
        }
        if (releaseKeystorePassword != null) {
            create("release") {
                storeFile = file(System.getenv("RELEASE_KEYSTORE_PATH") ?: "../release.keystore")
                storePassword = releaseKeystorePassword
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "diegesis"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: releaseKeystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (releaseKeystorePassword != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    tasks.withType<KotlinCompile>().configureEach {
        // :ai's public API surface uses kotlin.uuid.Uuid and kotlin.time.Instant.
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(project(":ai"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
}
