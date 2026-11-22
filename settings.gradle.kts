pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Flutter 3.47 rejects the Kotlin Gradle Plugin versions used by older
    // generated module templates. Keep the host and Add-to-App toolchain aligned.
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
}

dependencyResolutionManagement {
    // Flutter's Add-to-App Gradle plugin adds its engine Maven repository to the
    // project. Allow that repository while retaining the shared Google/Maven Central
    // declarations above.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "fxxkHilife"
include(":app")

// Flutter Add-to-App generates this script after `flutter pub get`.
// Keeping the inclusion here makes the native host independent from Flutter page code while
// allowing the cloud build to assemble one APK with the new presentation module.
val flutterInclude = file("flutter_ui/.android/include_flutter.groovy")
if (flutterInclude.isFile) {
    apply(from = flutterInclude)
}
