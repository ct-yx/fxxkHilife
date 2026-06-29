package com.freebuds.controller.ui

import android.content.Context

enum class UiDisplayMode(val label: String, val description: String) {
    CLASSIC("传统展示", "稳定、清晰、性能开销低"),
    LIQUID_GLASS("液态玻璃", "壁纸、毛玻璃、虹彩边缘与漂浮质感")
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
