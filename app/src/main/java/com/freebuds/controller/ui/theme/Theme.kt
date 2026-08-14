package com.freebuds.controller.ui.theme

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary          = Color(0xFF82B4FF),
    onPrimary        = Color(0xFF003063),
    primaryContainer = Color(0xFF00468A),
    surface          = Color(0xFF111318),
    surfaceVariant   = Color(0xFF43474E),
    background       = Color(0xFF111318),
    onBackground     = Color(0xFFE2E2E9),
    onSurface        = Color(0xFFE2E2E9),
)

private val LightColors = lightColorScheme(
    primary          = Color(0xFF005DBD),
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E3FF),
    surface          = Color(0xFFFDFBFF),
    surfaceVariant   = Color(0xFFE0E2EC),
    background       = Color(0xFFFDFBFF),
    onBackground     = Color(0xFF1A1C1E),
    onSurface        = Color(0xFF1A1C1E),
)

enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Composable
fun AppTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    // 同步到系统全局
    SideEffect {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Compatibility entry point kept while callers migrate to the shared AppTheme name. */
@Composable
fun FxxkHilifeTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) = AppTheme(mode = mode, content = content)
