package com.freebuds.controller.ui.glass

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/** User-tunable liquid glass parameters persisted from Settings > Personalization. */
data class LiquidGlassConfig(
    val tintAlpha: Float = 0.12f,
    val refractionStrength: Float = 0.72f,
    val depth: Float = 0.42f,
    val cornerRadiusDp: Float = 28f,
    val surfaceProfile: GlassSurfaceProfile = GlassSurfaceProfile.Squircle,
)

val LocalLiquidGlassConfig = staticCompositionLocalOf { LiquidGlassConfig() }

private const val PREF_NAME = "fxxk_ui_glass"

fun loadLiquidGlassConfig(context: Context): LiquidGlassConfig {
    val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val profileName = prefs.getString("surface_profile", GlassSurfaceProfile.Squircle.name)
        ?: GlassSurfaceProfile.Squircle.name
    val profile = runCatching { GlassSurfaceProfile.valueOf(profileName) }
        .getOrDefault(GlassSurfaceProfile.Squircle)
    return LiquidGlassConfig(
        tintAlpha = prefs.getFloat("tint_alpha", 0.12f).coerceIn(0.04f, 0.20f),
        refractionStrength = prefs.getFloat("refraction_strength", 0.72f).coerceIn(0.30f, 1.00f),
        depth = prefs.getFloat("depth", 0.42f).coerceIn(0.10f, 0.80f),
        cornerRadiusDp = prefs.getFloat("corner_radius_dp", 28f).coerceIn(16f, 42f),
        surfaceProfile = profile,
    )
}

fun saveLiquidGlassConfig(context: Context, config: LiquidGlassConfig) {
    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        .edit()
        .putFloat("tint_alpha", config.tintAlpha)
        .putFloat("refraction_strength", config.refractionStrength)
        .putFloat("depth", config.depth)
        .putFloat("corner_radius_dp", config.cornerRadiusDp)
        .putString("surface_profile", config.surfaceProfile.name)
        .apply()
}
