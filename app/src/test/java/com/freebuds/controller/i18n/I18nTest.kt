package com.freebuds.controller.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class I18nTest {
    @Test
    fun languagePacksUseTheSameKeySet() {
        val expected = I18n.keys(I18nLocale.ZH_CN)
        I18nLocale.entries.forEach { locale ->
            assertEquals("Key mismatch for ${locale.tag}", expected, I18n.keys(locale))
        }
    }

    @Test
    fun englishAndTraditionalChineseTranslateRepresentativeUiText() {
        assertEquals("Settings", I18n.provider(I18nLocale.EN).t("settings.title"))
        assertEquals("設定", I18n.provider(I18nLocale.ZH_TW).t("settings.title"))
        assertEquals("型號", I18n.provider(I18nLocale.ZH_TW).t("device.model"))
        assertTrue(I18n.provider(I18nLocale.EN).t("permission.description").contains("Bluetooth"))
    }
}
