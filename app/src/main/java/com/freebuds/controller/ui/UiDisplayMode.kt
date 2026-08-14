package com.freebuds.controller.ui

import com.freebuds.controller.i18n.I18n

enum class UiDisplayMode(private val labelKey: String, private val descriptionKey: String) {
    CLASSIC("ui.display.classic", "ui.display.classic_desc"),
    LIQUID_GLASS("ui.display.liquid_glass", "ui.display.liquid_glass_desc");

    val label: String get() = I18n.t(labelKey)
    val description: String get() = I18n.t(descriptionKey)
}
