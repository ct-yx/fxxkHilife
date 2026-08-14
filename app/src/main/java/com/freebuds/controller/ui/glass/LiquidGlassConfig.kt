package com.freebuds.controller.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf

enum class GlassRendererMode {
    HAZE_2,
}

/** User-tunable liquid glass parameters persisted from Settings > Personalization. */
data class LiquidGlassConfig(
    val rendererMode: GlassRendererMode = GlassRendererMode.HAZE_2,
    val tintAlpha: Float = 0.12f,
    val readabilityStrength: Float = 0.28f,
    val refractionStrength: Float = 0.72f,
    val depth: Float = 0.42f,
    val cornerRadiusDp: Float = 28f,
    val surfaceProfile: GlassSurfaceProfile = GlassSurfaceProfile.Squircle,
)

val LocalLiquidGlassConfig = staticCompositionLocalOf { LiquidGlassConfig() }
