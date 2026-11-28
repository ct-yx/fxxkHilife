import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    // Apply after Android so Kotlin resolves the AGP variant API from the same
    // plugin classpath used by the host module.
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.freebuds.controller"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.freebuds.controller"
        minSdk = 26
        targetSdk = 35
        versionCode = 107
        versionName = "4.7.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("fixedDebug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixedDebug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("fixedDebug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(project(":flutter"))

    testImplementation("junit:junit:4.13.2")
    // Android's local-test android.jar provides JSONObject stubs only; use the
    // JVM implementation so update-manifest parsing tests exercise real JSON.
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
