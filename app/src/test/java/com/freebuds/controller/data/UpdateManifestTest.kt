package com.freebuds.controller.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    @Test
    fun parsesStableManifestWithVersionCodeAndIntegrityFields() {
        val manifest = UpdateManifest.fromJson(
            JSONObject(
                """
                {
                  "schemaVersion": 1,
                  "channel": "stable",
                  "versionName": "4.4.0",
                  "versionCode": 99,
                  "releaseUrl": "https://github.com/ct-yx/fxxkHilife/releases/tag/v4.4.0",
                  "apkUrl": "https://github.com/ct-yx/fxxkHilife/releases/download/v4.4.0/app.apk",
                  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                  "publishedAt": "2026-08-14T00:00:00Z",
                  "minSdk": 26,
                  "notesUrl": "https://github.com/ct-yx/fxxkHilife/releases/tag/v4.4.0"
                }
                """.trimIndent(),
            )
        )

        assertEquals(UpdateChannel.STABLE, manifest.channel)
        assertEquals(99L, manifest.versionCode)
        assertEquals(64, manifest.sha256.length)
        assertTrue(manifest.apkUrl.startsWith("https://"))
    }

    @Test
    fun missingOptionalNotesAndApkRemainEmptyForSameVersionManifest() {
        val manifest = UpdateManifest.fromJson(
            JSONObject(
                """
                {
                  "schemaVersion": 1,
                  "channel": "stable",
                  "versionName": "4.3.10",
                  "versionCode": 98,
                  "releaseUrl": "https://github.com/ct-yx/fxxkHilife/releases/tag/v4.3.10",
                  "publishedAt": "2026-08-14T00:00:00Z",
                  "minSdk": 26,
                  "notesUrl": ""
                }
                """.trimIndent(),
            )
        )

        assertEquals("", manifest.apkUrl)
        assertEquals("", manifest.sha256)
        assertEquals("", manifest.notes)
    }
}
