package com.freebuds.controller.util

import android.content.Context
import android.os.Process
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

    private val installed = AtomicBoolean(false)
    @Volatile private var reportFile: File? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return

        val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)
        reportFile = File(directory, FILE_NAME)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeReport(directory, thread.name, throwable)
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
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
