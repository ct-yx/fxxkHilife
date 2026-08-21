package com.freebuds.controller.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Imports a picked image into app-private storage so the UI does not depend on a temporary
 * DocumentsUI grant after a process restart.
 */
object WallpaperStore {
    private const val DIRECTORY_NAME = "wallpapers"
    private const val CURRENT_FILE_NAME = "current"
    private const val TEMP_FILE_NAME = "current.part"

    fun importWallpaper(context: Context, source: Uri): String? {
        val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)
        if (!directory.exists() && !directory.mkdirs()) return null

        val temporary = File(directory, TEMP_FILE_NAME)
        val target = File(directory, CURRENT_FILE_NAME)
        return try {
            temporary.delete()
            val input = context.contentResolver.openInputStream(source) ?: return null
            input.use { stream ->
                temporary.outputStream().use { output -> stream.copyTo(output) }
            }
            if (temporary.length() <= 0L) return null
            if (target.exists() && !target.delete()) return null
            if (!temporary.renameTo(target)) return null
            Uri.fromFile(target).toString()
        } catch (_: Exception) {
            null
        } finally {
            if (!target.exists()) temporary.delete()
        }
    }

    fun clear(context: Context, storedUri: String?) {
        val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)
        val target = File(directory, CURRENT_FILE_NAME)
        if (storedUri == null || storedUri == Uri.fromFile(target).toString()) {
            target.delete()
        }
        File(directory, TEMP_FILE_NAME).delete()
    }
}
