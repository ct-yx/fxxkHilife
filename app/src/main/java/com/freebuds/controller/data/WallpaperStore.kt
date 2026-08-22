package com.freebuds.controller.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.util.UUID

/**
 * Imports a picked image into app-private storage so the UI does not depend on a temporary
 * DocumentsUI grant after a process restart.
 */
object WallpaperStore {
    private const val DIRECTORY_NAME = "wallpapers"
    private const val TEMP_FILE_NAME = "current.part"
    private const val FILE_PREFIX = "wallpaper-"

    fun importWallpaper(context: Context, source: Uri): String? {
        val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)
        if (!directory.exists() && !directory.mkdirs()) return null

        val temporary = File(directory, TEMP_FILE_NAME)
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(context.contentResolver.getType(source))
            ?.let { ".$it" }
            ?: ".img"
        val target = File(directory, "$FILE_PREFIX${UUID.randomUUID()}$extension")
        return try {
            temporary.delete()
            val input = context.contentResolver.openInputStream(source) ?: return null
            input.use { stream ->
                temporary.outputStream().use { output -> stream.copyTo(output) }
            }
            if (temporary.length() <= 0L) return null
            if (!temporary.renameTo(target)) return null
            directory.listFiles()
                ?.filter { it.name.startsWith(FILE_PREFIX) && it != target }
                ?.forEach { it.delete() }
            Uri.fromFile(target).toString()
        } catch (_: Exception) {
            null
        } finally {
            if (!target.exists()) temporary.delete()
        }
    }

    fun clear(context: Context, storedUri: String?) {
        val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)
        directory.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) }
            ?.forEach { it.delete() }
        File(directory, TEMP_FILE_NAME).delete()
    }
}
