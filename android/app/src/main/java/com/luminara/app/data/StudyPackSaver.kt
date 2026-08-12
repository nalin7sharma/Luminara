package com.luminara.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Where a finished study pack ended up, and how to open it. */
data class SavedPack(
    val uri: Uri,
    val displayName: String,
    val isPdf: Boolean,
    val location: String,
)

/**
 * Puts the downloaded study pack somewhere the student can actually find it.
 *
 * On Android 10+ that is the shared Downloads collection via MediaStore, which
 * needs no runtime permission. On older devices we keep it in the app's own
 * external files directory — still permission-free, and we tell the student
 * exactly where it went rather than silently choosing for them.
 */
object StudyPackSaver {

    fun save(
        context: Context,
        source: File,
        displayName: String,
        isPdf: Boolean,
    ): SavedPack {
        val mime = if (isPdf) "application/pdf" else "text/html"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return saveToAppStorage(context, source, displayName, isPdf)

            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            return SavedPack(uri, displayName, isPdf, "Downloads")
        }

        return saveToAppStorage(context, source, displayName, isPdf)
    }

    private fun saveToAppStorage(
        context: Context,
        source: File,
        displayName: String,
        isPdf: Boolean,
    ): SavedPack {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val target = File(dir, displayName)
        source.copyTo(target, overwrite = true)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target,
        )
        return SavedPack(uri, displayName, isPdf, "Luminara documents folder")
    }

    fun openIntent(pack: SavedPack): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(pack.uri, if (pack.isPdf) "application/pdf" else "text/html")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun shareIntent(pack: SavedPack): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = if (pack.isPdf) "application/pdf" else "text/html"
            putExtra(Intent.EXTRA_STREAM, pack.uri)
            putExtra(Intent.EXTRA_SUBJECT, pack.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        "Share study pack",
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}
