// Plugin versions are declared in settings.gradle.kts so the Android host and
// the generated Flutter module resolve one consistent plugin classpath.

// Flutter's Add-to-App plugin adds download.flutter.io to every project. Keep
// the normal Android repositories on those same project-level repository lists
// so native dependencies are not restricted to the Flutter engine mirror.
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
