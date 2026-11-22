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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
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
