package com.eljihani.veinvision.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Captured stills live in app-specific external storage (removed on uninstall, not shared). */
class SessionStore(private val context: Context) {
    val dir: File
        get() = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) ?: context.filesDir, "captures")
            .also { it.mkdirs() }

    fun newFile(): File =
        File(dir, "VV_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + ".jpg")

    fun list(): List<File> = dir.listFiles { f -> f.extension.lowercase() == "jpg" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun delete(f: File): Boolean = f.delete()

    fun totalBytes(): Long = list().sumOf { it.length() }
}
