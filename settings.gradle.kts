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
    // Match the Flutter 3.47.0 template so the generated Add-to-App module and
    // the native host resolve one compatible Android/Kotlin plugin classpath.
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
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
