package com.freebuds.controller.ui

import android.content.Context
import com.freebuds.controller.i18n.I18n

enum class UiDisplayMode(private val labelKey: String, private val descriptionKey: String) {
    CLASSIC("ui.display.classic", "ui.display.classic_desc"),
    LIQUID_GLASS("ui.display.liquid_glass", "ui.display.liquid_glass_desc");

    val label: String get() = I18n.t(labelKey)
    val description: String get() = I18n.t(descriptionKey)
}

fun loadUiDisplayMode(context: Context): UiDisplayMode {
    val name = context.getSharedPreferences("fxxk_ui", Context.MODE_PRIVATE)
        .getString("display_mode", UiDisplayMode.CLASSIC.name) ?: UiDisplayMode.CLASSIC.name
    return runCatching { UiDisplayMode.valueOf(name) }.getOrDefault(UiDisplayMode.CLASSIC)
}

fun saveUiDisplayMode(context: Context, mode: UiDisplayMode) {
    context.getSharedPreferences("fxxk_ui", Context.MODE_PRIVATE)
        .edit()
        .putString("display_mode", mode.name)
        .apply()
}
