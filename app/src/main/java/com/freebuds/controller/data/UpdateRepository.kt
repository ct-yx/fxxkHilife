package com.freebuds.controller.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.freebuds.controller.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateManifest(
    val schemaVersion: Int,
    val channel: UpdateChannel,
    val versionName: String,
    val versionCode: Long,
    val releaseUrl: String,
    val apkUrl: String,
    val sha256: String,
    val publishedAt: String,
    val minSdk: Int,
    val notesUrl: String,
    val notes: String = "",
) {
    val isNewer: Boolean get() = versionCode > BuildConfig.VERSION_CODE

    companion object {
        fun fromJson(json: JSONObject): UpdateManifest {
            val channel = runCatching {
                UpdateChannel.valueOf(json.optString("channel", UpdateChannel.STABLE.name).uppercase())
            }.getOrDefault(UpdateChannel.STABLE)
            return UpdateManifest(
                schemaVersion = json.getInt("schemaVersion"),
                channel = channel,
                versionName = json.getString("versionName"),
                versionCode = json.getLong("versionCode"),
                releaseUrl = json.getString("releaseUrl"),
                apkUrl = json.optString("apkUrl", ""),
                sha256 = json.optString("sha256", "").lowercase(),
                publishedAt = json.optString("publishedAt", ""),
                minSdk = json.optInt("minSdk", 26),
                notesUrl = json.optString("notesUrl", ""),
                notes = json.optString("notes", ""),
            )
        }
    }
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data class Checking(val startedAtMs: Long) : UpdateUiState
    data class UpToDate(val checkedAtMs: Long, val currentVersionName: String) : UpdateUiState
    data class Available(val manifest: UpdateManifest, val checkedAtMs: Long) : UpdateUiState
    data class Downloading(
        val manifest: UpdateManifest,
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : UpdateUiState
    data class ReadyToInstall(val manifest: UpdateManifest, val apkUri: Uri) : UpdateUiState
    data class Installing(val manifest: UpdateManifest) : UpdateUiState
    data class Error(val reason: UpdateErrorReason, val message: String, val retryable: Boolean) : UpdateUiState
}

enum class UpdateErrorReason {
    NETWORK,
    MANIFEST_INVALID,
    NO_APK,
    HASH_MISMATCH,
    PACKAGE_MISMATCH,
    VERSION_MISMATCH,
    SIGNATURE_MISMATCH,
    INSTALL_PERMISSION,
    CANCELLED,
    UNKNOWN,
}

/**
 * Network, download and APK validation boundary for the settings UI.
 *
 * The repository never starts from a Bluetooth callback. Automatic checks are throttled by the
 * settings repository and all downloads use a temporary cache file until every validation passes.
 */
class UpdateRepository(
    private val context: Context,
    private val settings: SettingsRepository = SettingsRepository(context),
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    fun check(force: Boolean = false) {
        val currentSettings = settings.updateSettings()
        val now = System.currentTimeMillis()
        if (!force && (!currentSettings.autoCheckEnabled || now - currentSettings.lastCheckAtMs < currentSettings.checkIntervalMs)) {
            return
        }
        if (checkJob?.isActive == true) return
        _state.value = UpdateUiState.Checking(now)
        checkJob = scope.launch {
            try {
                val manifest = fetchManifest()
                settings.setUpdateSettings(currentSettings.copy(lastCheckAtMs = System.currentTimeMillis()))
                val nextState = if (manifest.isNewer) {
                    UpdateUiState.Available(manifest, System.currentTimeMillis())
                } else {
                    UpdateUiState.UpToDate(System.currentTimeMillis(), BuildConfig.VERSION_NAME)
                }
                _state.value = nextState
                if (manifest.isNewer && currentSettings.autoDownloadEnabled) {
                    download(manifest)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: UpdateException) {
                _state.value = UpdateUiState.Error(e.reason, e.message ?: e.reason.name, e.retryable)
            } catch (e: Exception) {
                _state.value = UpdateUiState.Error(UpdateErrorReason.NETWORK, e.message ?: "network error", true)
            }
        }
    }

    fun download(manifest: UpdateManifest? = currentManifest()) {
        val target = manifest ?: run {
            _state.value = UpdateUiState.Error(UpdateErrorReason.NO_APK, "No update is available", false)
            return
        }
        if (target.apkUrl.isBlank()) {
            _state.value = UpdateUiState.Error(UpdateErrorReason.NO_APK, "No APK asset in update manifest", false)
            return
        }
        downloadJob?.cancel()
        downloadJob = scope.launch {
            val temporary = File(appContext.cacheDir, "updates/${target.versionCode}.apk.part")
            val completed = File(appContext.cacheDir, "updates/${target.versionCode}.apk")
            try {
                temporary.parentFile?.mkdirs()
                temporary.delete()
                completed.delete()
                downloadTo(target, temporary)
                validateApk(target, temporary)
                if (!temporary.renameTo(completed)) throw UpdateException(UpdateErrorReason.UNKNOWN, "Unable to finalize APK", true)
                val uri = FileProvider.getUriForFile(appContext, "${BuildConfig.APPLICATION_ID}.fileprovider", completed)
                _state.value = UpdateUiState.ReadyToInstall(target, uri)
            } catch (cancelled: CancellationException) {
                temporary.delete()
                _state.value = UpdateUiState.Error(UpdateErrorReason.CANCELLED, "Download cancelled", true)
            } catch (e: UpdateException) {
                temporary.delete()
                completed.delete()
                _state.value = UpdateUiState.Error(e.reason, e.message ?: e.reason.name, e.retryable)
            } catch (e: Exception) {
                temporary.delete()
                completed.delete()
                _state.value = UpdateUiState.Error(UpdateErrorReason.NETWORK, e.message ?: "download error", true)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun install(manifest: UpdateManifest? = currentManifest()): Intent? {
        val target = manifest ?: return null
        val ready = _state.value as? UpdateUiState.ReadyToInstall ?: return null
        if (ready.manifest.versionCode != target.versionCode) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !appContext.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateUiState.Error(UpdateErrorReason.INSTALL_PERMISSION, "Install permission is disabled", false)
            return null
        }
        _state.value = UpdateUiState.Installing(target)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(ready.apkUri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun currentManifest(): UpdateManifest? = when (val state = _state.value) {
        is UpdateUiState.Available -> state.manifest
        is UpdateUiState.Downloading -> state.manifest
        is UpdateUiState.ReadyToInstall -> state.manifest
        else -> null
    }

    private fun fetchManifest(): UpdateManifest {
        var lastError: UpdateException? = null
        listOf(MANIFEST_URL, RELEASE_MANIFEST_URL).forEach { source ->
            try {
                return readManifest(source)
            } catch (error: UpdateException) {
                lastError = error
            }
        }
        throw lastError ?: UpdateException(UpdateErrorReason.NETWORK, "Unable to load update manifest", true)
    }

    private fun readManifest(source: String): UpdateManifest {
        val connection = openHttpsConnection(source)
        return try {
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val manifest = runCatching { UpdateManifest.fromJson(JSONObject(text)) }
                .getOrElse {
                    throw UpdateException(UpdateErrorReason.MANIFEST_INVALID, "Invalid update manifest", false)
                }
            validateManifest(manifest)
            manifest
        } finally {
            connection.disconnect()
        }
    }

    private fun validateManifest(manifest: UpdateManifest) {
        if (manifest.schemaVersion != 1 || manifest.channel != UpdateChannel.STABLE ||
            manifest.versionName.isBlank() || manifest.versionCode <= 0L ||
            manifest.minSdk > Build.VERSION.SDK_INT
        ) {
            throw UpdateException(UpdateErrorReason.MANIFEST_INVALID, "Unsupported update manifest", false)
        }
        validateAllowedUrl(manifest.releaseUrl, required = true)
        if (manifest.apkUrl.isNotBlank()) validateAllowedUrl(manifest.apkUrl, required = false)
        if (manifest.notesUrl.isNotBlank()) validateAllowedUrl(manifest.notesUrl, required = false)
        if (manifest.isNewer && !SHA256_PATTERN.matches(manifest.sha256)) {
            throw UpdateException(UpdateErrorReason.MANIFEST_INVALID, "Update hash is missing or malformed", false)
        }
    }

    private fun validateAllowedUrl(rawUrl: String, required: Boolean) {
        if (rawUrl.isBlank() && !required) return
        val url = runCatching { URL(rawUrl) }
            .getOrElse { throw UpdateException(UpdateErrorReason.MANIFEST_INVALID, "Invalid update URL", false) }
        if (url.protocol != "https" || url.host !in ALLOWED_HOSTS) {
            throw UpdateException(UpdateErrorReason.MANIFEST_INVALID, "Update URL is not allowed", false)
        }
    }

    private suspend fun downloadTo(manifest: UpdateManifest, target: File) {
        val connection = openHttpsConnection(manifest.apkUrl)
        try {
            val total = connection.contentLengthLong.takeIf { it > 0L }
            var downloaded = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        _state.value = UpdateUiState.Downloading(manifest, downloaded, total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateApk(manifest: UpdateManifest, file: File) {
        if (manifest.sha256.isBlank()) throw UpdateException(UpdateErrorReason.MANIFEST_INVALID, "Missing APK hash", false)
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        if (!digest.digest().joinToString("") { "%02x".format(it) }.equals(manifest.sha256, ignoreCase = true)) {
            throw UpdateException(UpdateErrorReason.HASH_MISMATCH, "APK hash does not match manifest", false)
        }
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageArchiveInfo(
                file.path,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES)
        } ?: throw UpdateException(UpdateErrorReason.PACKAGE_MISMATCH, "APK manifest is unreadable", false)
        if (info.packageName != BuildConfig.APPLICATION_ID) throw UpdateException(UpdateErrorReason.PACKAGE_MISMATCH, "APK package does not match", false)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        if (versionCode != manifest.versionCode || versionCode <= BuildConfig.VERSION_CODE) {
            throw UpdateException(UpdateErrorReason.VERSION_MISMATCH, "APK version does not match manifest", false)
        }
        val current = appContext.packageManager.getPackageInfo(appContext.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val downloadedSigners = info.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            val currentSigners = current.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            if (downloadedSigners.isEmpty() || downloadedSigners != currentSigners) {
                throw UpdateException(UpdateErrorReason.SIGNATURE_MISMATCH, "APK signature does not match", false)
            }
        }
    }

    private fun openHttpsConnection(rawUrl: String, redirectCount: Int = 0): HttpURLConnection {
        val url = runCatching { URL(rawUrl) }.getOrElse { throw UpdateException(UpdateErrorReason.NETWORK, "Invalid update URL", false) }
        if (url.protocol != "https" || url.host !in ALLOWED_HOSTS) {
            throw UpdateException(UpdateErrorReason.NETWORK, "Update URL is not allowed", false)
        }
        val connection = (url.openConnection() as? HttpURLConnection)?.apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json, application/vnd.android.package-archive")
        } ?: throw UpdateException(UpdateErrorReason.NETWORK, "Unsupported update connection", true)
        val response = connection.responseCode
        if (response in 300..399) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location.isNullOrBlank() || redirectCount >= 3) {
                throw UpdateException(UpdateErrorReason.NETWORK, "Too many or invalid update redirects", true)
            }
            return openHttpsConnection(URL(url, location).toString(), redirectCount + 1)
        }
        if (response !in 200..299) {
            connection.disconnect()
            throw UpdateException(UpdateErrorReason.NETWORK, "HTTP $response", true)
        }
        return connection
    }

    fun close() {
        checkJob?.cancel()
        downloadJob?.cancel()
        scope.cancel()
    }

    private class UpdateException(
        val reason: UpdateErrorReason,
        override val message: String,
        val retryable: Boolean,
    ) : IOException(message)

    companion object {
        private const val MANIFEST_URL = "https://ct-yx.github.io/fxxkHilife/update.json"
        private const val RELEASE_MANIFEST_URL = "https://github.com/ct-yx/fxxkHilife/releases/latest/download/update.json"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private val ALLOWED_HOSTS = setOf(
            "ct-yx.github.io",
            "github.com",
            "api.github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )
        private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
    }
}
