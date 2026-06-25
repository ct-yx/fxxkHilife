package com.freebuds.controller.data

import com.freebuds.controller.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object UpdateChecker {

    private const val API_URL = "https://api.github.com/repos/ct-yx/fxxkHilife/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val publishedAt: String
    )

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_URL).openConnection()
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val tagName = json.getString("tag_name")  // "v2.0.0-alpha.1"
            val versionName = tagName.removePrefix("v")

            val assets = json.getJSONArray("assets")
            val downloadUrl = if (assets.length() > 0) {
                assets.getJSONObject(0).getString("browser_download_url")
            } else ""

            UpdateInfo(
                latestVersion = versionName,
                downloadUrl = downloadUrl,
                releaseNotes = json.optString("body", "").take(200),
                publishedAt = json.optString("published_at", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun hasUpdate(info: UpdateInfo?): Boolean {
        if (info == null) return false
        val current = BuildConfig.VERSION_NAME  // "2.0.0-alpha.1"
        return compareVersions(info.latestVersion, current) > 0
    }

    private fun compareVersions(a: String, b: String): Int {
        val partsA = a.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val partsB = b.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(partsA.size, partsB.size)) {
            val diff = (partsA.getOrElse(i) { 0 }) - (partsB.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }
}
