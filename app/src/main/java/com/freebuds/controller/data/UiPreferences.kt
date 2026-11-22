package com.freebuds.controller.data

/** Application preferences shared by the native services and the Flutter UI. */
enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
    ;

    companion object {
        fun fromApi(value: String?): ThemeMode = when (value?.lowercase()) {
            "dark" -> DARK
            "light" -> LIGHT
            else -> SYSTEM
        }
    }
}

enum class WallpaperScope {
    ALL,
    HOME,
    SETTINGS,
    ;

    companion object {
        fun fromApi(value: String?): WallpaperScope = when (value?.lowercase()) {
            "home" -> HOME
            "settings" -> SETTINGS
            else -> ALL
        }
    }
}
