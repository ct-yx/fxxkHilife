package com.freebuds.controller.util

import android.content.Context
import android.os.Process
import com.freebuds.controller.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * 将未捕获异常保存到应用私有目录，便于下次启动后随诊断报告导出。
 *
 * Android 的 logcat 属于系统进程日志，普通应用自己的诊断导出不会自动包含它。这里
 * 保留一份最近崩溃的文本副本，不依赖外部权限，也不改变系统默认崩溃处理流程。
 */
object CrashReporter {

    private const val DIRECTORY_NAME = "crash"
    private const val FILE_NAME = "latest.txt"
    private const val TEMP_FILE_NAME = "latest.part"
    private const val PREFERENCES_NAME = "crash_reporter"
    private const val SAFE_MODE_KEY = "wallpaper_glass_safe_mode"
    private const val SAFE_MODE_VERSION_KEY = "wallpaper_glass_safe_mode_version"
    private const val CONSUMED_REPORT_KEY = "wallpaper_glass_consumed_report"

    private val installed = AtomicBoolean(false)
    @Volatile private var reportFile: File? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return

        val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)
        reportFile = File(directory, FILE_NAME)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            markWallpaperGlassSafeMode(context)
            writeReport(directory, thread.name, throwable)
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /**
     * 只在检测到上一次未处理的异常时关闭壁纸玻璃，避免启动阶段再次进入同一崩溃路径。
     * 报告指纹只消费一次：升级到新版本后会重新允许玻璃；同一版本重复启动则保持 fallback，
     * 让用户可以进入设置页导出完整堆栈。
     */
    fun isWallpaperGlassSafeMode(context: Context): Boolean {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val currentVersion = BuildConfig.VERSION_CODE
        if (preferences.getBoolean(SAFE_MODE_KEY, false) &&
            preferences.getInt(SAFE_MODE_VERSION_KEY, -1) == currentVersion
        ) {
            return true
        }

        val report = latestReport() ?: return false
        val fingerprint = reportFingerprint(report)
        if (fingerprint.isBlank() || preferences.getString(CONSUMED_REPORT_KEY, null) == fingerprint) {
            return false
        }

        preferences.edit()
            .putBoolean(SAFE_MODE_KEY, true)
            .putInt(SAFE_MODE_VERSION_KEY, currentVersion)
            .putString(CONSUMED_REPORT_KEY, fingerprint)
            .commit()
        return true
    }

    private fun markWallpaperGlassSafeMode(context: Context) {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SAFE_MODE_KEY, true)
            .putInt(SAFE_MODE_VERSION_KEY, BuildConfig.VERSION_CODE)
            .commit()
    }

    private fun reportFingerprint(report: String): String {
        val timestamp = report.lineSequence().firstOrNull { it.startsWith("timestamp=") }.orEmpty()
        val exception = report.lineSequence().firstOrNull { it.startsWith("exception=") }.orEmpty()
        return "$timestamp|$exception"
    }

    fun latestReport(): String? {
        val file = reportFile ?: return null
        return runCatching {
            file.takeIf { it.isFile && it.length() > 0L }?.readText()
        }.getOrNull()
    }

    private fun writeReport(directory: File, threadName: String, throwable: Throwable) {
        runCatching {
            if (!directory.exists()) directory.mkdirs()
            val stackTrace = StringWriter().also { writer ->
                throwable.printStackTrace(PrintWriter(writer))
            }.toString()
            val text = buildString {
                appendLine("fxxkHilife uncaught exception")
                appendLine("appVersion=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("timestamp=${Instant.now()}")
                appendLine("thread=$threadName")
                appendLine("exception=${throwable.javaClass.name}")
                appendLine("--- stacktrace ---")
                append(stackTrace)
            }
            val temporary = File(directory, TEMP_FILE_NAME)
            temporary.writeText(text)
            val target = File(directory, FILE_NAME)
            if (!temporary.renameTo(target)) target.writeText(text)
            if (temporary.exists()) temporary.delete()
        }
    }
}
