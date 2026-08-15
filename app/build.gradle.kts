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
        versionCode = 1
        versionName = "0.1.0"
    }

    // Pinned debug keystore so every CI build signs identically and
    // updates install in place (no uninstall, no data loss).
    signingConfigs {
        named("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "diegesis-debug"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
