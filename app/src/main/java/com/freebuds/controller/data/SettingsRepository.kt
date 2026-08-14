package com.freebuds.controller.data

import android.content.Context
import com.freebuds.controller.ui.UiDisplayMode
import com.freebuds.controller.ui.WallpaperScope
import com.freebuds.controller.ui.glass.GlassRendererMode
import com.freebuds.controller.ui.glass.GlassSurfaceProfile
import com.freebuds.controller.ui.glass.LiquidGlassConfig
import com.freebuds.controller.ui.theme.ThemeMode
import com.freebuds.controller.i18n.I18nLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single persistence boundary for app-facing preferences.
 *
 * The old preference files remain readable so an upgrade does not reset the user's theme,
 * wallpaper or diagnostics settings. Composables only receive values and callbacks; they do not
 * own a SharedPreferences instance.
 */
class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val settings = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(SettingsSnapshot())
    val state: StateFlow<SettingsSnapshot> = _state.asStateFlow()

    init {
        migrateLegacyValues()
        refreshState()
    }

    fun themeMode(): ThemeMode = state.value.themeMode

    fun setThemeMode(value: ThemeMode) {
        settings.edit().putString(KEY_THEME, value.name).apply()
        refreshState()
    }

    fun locale(): I18nLocale = state.value.locale

    fun setLocale(value: I18nLocale) {
        settings.edit().putString(KEY_LOCALE, value.tag).apply()
        refreshState()
    }

    fun displayMode(): UiDisplayMode = state.value.displayMode

    fun setDisplayMode(value: UiDisplayMode) {
        settings.edit().putString(KEY_DISPLAY_MODE, value.name).apply()
        refreshState()
    }

    fun wallpaperUri(): String? = state.value.wallpaperUri

    fun setWallpaperUri(value: String?) {
        settings.edit().apply {
            if (value == null) remove(KEY_WALLPAPER_URI) else putString(KEY_WALLPAPER_URI, value)
        }.apply()
        refreshState()
    }

    fun wallpaperScope(): WallpaperScope = state.value.wallpaperScope

    fun setWallpaperScope(value: WallpaperScope) {
        settings.edit().putString(KEY_WALLPAPER_SCOPE, value.name).apply()
        refreshState()
    }

    fun autoLowLatency(): Boolean = state.value.autoLowLatency

    fun setAutoLowLatency(value: Boolean) {
        settings.edit().putBoolean(KEY_AUTO_LOW_LATENCY, value).apply()
        refreshState()
    }

    fun logMaxLines(defaultValue: Int): Int = state.value.logMaxLines.takeIf { settings.contains(KEY_LOG_MAX_LINES) } ?: defaultValue

    fun setLogMaxLines(value: Int) {
        settings.edit().putInt(KEY_LOG_MAX_LINES, value).apply()
        refreshState()
    }

    fun protocolFrameLogging(defaultValue: Boolean): Boolean =
        if (settings.contains(KEY_PROTOCOL_FRAMES)) state.value.protocolFrameLogging else defaultValue

    fun setProtocolFrameLogging(value: Boolean) {
        settings.edit().putBoolean(KEY_PROTOCOL_FRAMES, value).apply()
        refreshState()
    }

    fun updateSettings(): UpdateSettings = state.value.updateSettings

    fun setUpdateSettings(value: UpdateSettings) {
        settings.edit()
            .putString(KEY_UPDATE_CHANNEL, value.channel.name)
            .putBoolean(KEY_AUTO_CHECK, value.autoCheckEnabled)
            .putBoolean(KEY_AUTO_DOWNLOAD, value.autoDownloadEnabled)
            .putLong(KEY_CHECK_INTERVAL, value.checkIntervalMs)
            .putLong(KEY_LAST_CHECK_AT, value.lastCheckAtMs)
            .putLong(KEY_LAST_NOTIFIED_VERSION, value.lastNotifiedVersionCode)
            .putBoolean(KEY_METERED_ALLOWED, value.meteredNetworkAllowed)
            .apply()
        refreshState()
    }

    fun glassConfig(): LiquidGlassConfig = state.value.glassConfig

    fun setGlassConfig(value: LiquidGlassConfig) {
        appContext.getSharedPreferences(GLASS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("renderer_mode", value.rendererMode.name)
            .putFloat("tint_alpha", value.tintAlpha)
            .putFloat("readability_strength", value.readabilityStrength)
            .putFloat("refraction_strength", value.refractionStrength)
            .putFloat("depth", value.depth)
            .putFloat("corner_radius_dp", value.cornerRadiusDp)
            .putString("surface_profile", value.surfaceProfile.name)
            .apply()
        refreshState()
    }

    private fun readSnapshot(): SettingsSnapshot = SettingsSnapshot(
        themeMode = enumValue(settings.getString(KEY_THEME, null), ThemeMode.SYSTEM),
        locale = I18nLocale.fromTag(settings.getString(KEY_LOCALE, null)),
        displayMode = enumValue(settings.getString(KEY_DISPLAY_MODE, null), UiDisplayMode.CLASSIC),
        wallpaperUri = settings.getString(KEY_WALLPAPER_URI, null),
        wallpaperScope = enumValue(settings.getString(KEY_WALLPAPER_SCOPE, null), WallpaperScope.ALL),
        autoLowLatency = settings.getBoolean(KEY_AUTO_LOW_LATENCY, true),
        logMaxLines = settings.getInt(KEY_LOG_MAX_LINES, DEFAULT_LOG_MAX_LINES),
        protocolFrameLogging = settings.getBoolean(KEY_PROTOCOL_FRAMES, false),
        glassConfig = readGlassConfig(),
        updateSettings = UpdateSettings(
            channel = settings.getString(KEY_UPDATE_CHANNEL, UpdateChannel.STABLE.name)
                .let { enumValue(it, UpdateChannel.STABLE) },
            autoCheckEnabled = settings.getBoolean(KEY_AUTO_CHECK, true),
            autoDownloadEnabled = settings.getBoolean(KEY_AUTO_DOWNLOAD, false),
            checkIntervalMs = settings.getLong(KEY_CHECK_INTERVAL, DEFAULT_CHECK_INTERVAL_MS),
            lastCheckAtMs = settings.getLong(KEY_LAST_CHECK_AT, 0L),
            lastNotifiedVersionCode = settings.getLong(KEY_LAST_NOTIFIED_VERSION, 0L),
            meteredNetworkAllowed = settings.getBoolean(KEY_METERED_ALLOWED, false),
        ),
    )

    private fun refreshState() {
        val next = readSnapshot()
        _state.value = next
        // These two settings control process-wide logging behavior, so applying them here keeps
        // the Settings page and the diagnostic service on the same typed source of truth.
        com.freebuds.controller.util.LogBuffer.setMaxLines(next.logMaxLines)
        com.freebuds.controller.util.LogBuffer.setProtocolFrameLogging(next.protocolFrameLogging)
    }

    private fun readGlassConfig(): LiquidGlassConfig {
        val prefs = appContext.getSharedPreferences(GLASS_PREFS, Context.MODE_PRIVATE)
        val profile = runCatching {
            GlassSurfaceProfile.valueOf(
                prefs.getString("surface_profile", GlassSurfaceProfile.Squircle.name)
                    ?: GlassSurfaceProfile.Squircle.name,
            )
        }.getOrDefault(GlassSurfaceProfile.Squircle)
        return LiquidGlassConfig(
            rendererMode = GlassRendererMode.HAZE_2,
            tintAlpha = prefs.getFloat("tint_alpha", 0.12f).coerceIn(0.04f, 0.20f),
            readabilityStrength = prefs.getFloat("readability_strength", 0.28f).coerceIn(0.00f, 0.70f),
            refractionStrength = prefs.getFloat("refraction_strength", 0.72f).coerceIn(0.30f, 1.00f),
            depth = prefs.getFloat("depth", 0.42f).coerceIn(0.10f, 0.80f),
            cornerRadiusDp = prefs.getFloat("corner_radius_dp", 28f).coerceIn(16f, 42f),
            surfaceProfile = profile,
        )
    }

    private fun migrateLegacyValues() {
        val editor = settings.edit()
        val legacyTheme = appContext.getSharedPreferences("fxxk_theme", Context.MODE_PRIVATE)
        val legacyUi = appContext.getSharedPreferences("fxxk_ui", Context.MODE_PRIVATE)
        val legacyI18n = appContext.getSharedPreferences("fxxk_i18n", Context.MODE_PRIVATE)
        val legacyDevice = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

        copyStringIfMissing(editor, KEY_THEME, legacyTheme, "theme_mode")
        copyStringIfMissing(editor, KEY_WALLPAPER_URI, legacyTheme, "wallpaper_uri")
        copyStringIfMissing(editor, KEY_WALLPAPER_SCOPE, legacyTheme, "wallpaper_scope")
        copyIntIfMissing(editor, KEY_LOG_MAX_LINES, legacyTheme, "log_max_lines")
        copyBooleanIfMissing(editor, KEY_PROTOCOL_FRAMES, legacyTheme, "log_protocol_frames")
        copyStringIfMissing(editor, KEY_DISPLAY_MODE, legacyUi, "display_mode")
        copyStringIfMissing(editor, KEY_LOCALE, legacyI18n, "locale")
        copyBooleanIfMissing(editor, KEY_AUTO_LOW_LATENCY, legacyDevice, "auto_low_latency")
        editor.apply()
    }

    private fun copyStringIfMissing(
        editor: android.content.SharedPreferences.Editor,
        target: String,
        source: android.content.SharedPreferences,
        sourceKey: String,
    ) {
        if (!settings.contains(target) && source.contains(sourceKey)) {
            source.getString(sourceKey, null)?.let { editor.putString(target, it) }
        }
    }

    private fun copyIntIfMissing(
        editor: android.content.SharedPreferences.Editor,
        target: String,
        source: android.content.SharedPreferences,
        sourceKey: String,
    ) {
        if (!settings.contains(target) && source.contains(sourceKey)) {
            editor.putInt(target, source.getInt(sourceKey, 0))
        }
    }

    private fun copyBooleanIfMissing(
        editor: android.content.SharedPreferences.Editor,
        target: String,
        source: android.content.SharedPreferences,
        sourceKey: String,
    ) {
        if (!settings.contains(target) && source.contains(sourceKey)) {
            editor.putBoolean(target, source.getBoolean(sourceKey, false))
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    companion object {
        private const val PREFS = "fxxk_settings"
        private const val GLASS_PREFS = "fxxk_ui_glass"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LOCALE = "locale"
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val KEY_WALLPAPER_URI = "wallpaper_uri"
        private const val KEY_WALLPAPER_SCOPE = "wallpaper_scope"
        private const val KEY_AUTO_LOW_LATENCY = "auto_low_latency"
        private const val KEY_LOG_MAX_LINES = "log_max_lines"
        private const val KEY_PROTOCOL_FRAMES = "log_protocol_frames"
        private const val KEY_UPDATE_CHANNEL = "update_channel"
        private const val KEY_AUTO_CHECK = "auto_check_enabled"
        private const val KEY_AUTO_DOWNLOAD = "auto_download_enabled"
        private const val KEY_CHECK_INTERVAL = "check_interval"
        private const val KEY_LAST_CHECK_AT = "last_check_at"
        private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version_code"
        private const val KEY_METERED_ALLOWED = "metered_network_allowed"
        private const val DEFAULT_LOG_MAX_LINES = 2_000
        const val DEFAULT_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}

enum class UpdateChannel { STABLE }

data class UpdateSettings(
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val autoCheckEnabled: Boolean = true,
    val autoDownloadEnabled: Boolean = false,
    val checkIntervalMs: Long = SettingsRepository.DEFAULT_CHECK_INTERVAL_MS,
    val lastCheckAtMs: Long = 0L,
    val lastNotifiedVersionCode: Long = 0L,
    val meteredNetworkAllowed: Boolean = false,
)

data class SettingsSnapshot(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val locale: I18nLocale = I18nLocale.ZH_CN,
    val displayMode: UiDisplayMode = UiDisplayMode.CLASSIC,
    val wallpaperUri: String? = null,
    val wallpaperScope: WallpaperScope = WallpaperScope.ALL,
    val autoLowLatency: Boolean = true,
    val logMaxLines: Int = 2_000,
    val protocolFrameLogging: Boolean = false,
    val glassConfig: LiquidGlassConfig = LiquidGlassConfig(),
    val updateSettings: UpdateSettings = UpdateSettings(),
)
