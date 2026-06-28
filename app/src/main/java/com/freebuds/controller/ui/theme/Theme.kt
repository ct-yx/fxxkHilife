package com.freebuds.controller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun FxxkHilifeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
