package com.example.aitoui.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Writes a backup zip into the device's public Downloads folder under a caller-chosen file name. On API 29+
 * this uses [MediaStore] (no storage permission required); on API 24-28 it falls back to a direct file in the
 * public Downloads directory, which requires the legacy storage permission (see [needsLegacyPermission]).
 *
 * Reading a backup for a restore does NOT go through here — the user picks the file via the Storage Access
 * Framework, so any backup zip (including one copied from another device) can be loaded without broad storage
 * permissions. See MainViewModel's Load flow.
 */
object DownloadsBackupStore {

    private const val MIME = "application/zip"

    /** Whether a runtime storage permission must be granted before reading/writing on this API level. */
    fun needsLegacyPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Opens [fileName] in Downloads for writing, overwriting any existing copy of that name in place. */
    fun openOutput(context: Context, fileName: String): OutputStream {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val existing = findUri(context, fileName)
            val uri = existing ?: run {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, MIME)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Could not create $fileName in Downloads")
            }
            // "wt" truncates, so an existing backup is fully replaced rather than appended to.
            return resolver.openOutputStream(uri, "wt")
                ?: throw IOException("Could not open $fileName for writing")
        }
        val file = legacyFile(fileName)
        file.parentFile?.mkdirs()
        return FileOutputStream(file)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findUri(context: Context, fileName: String): Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(fileName, "%${Environment.DIRECTORY_DOWNLOADS}%")
        context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return ContentUris.withAppendedId(collection, id)
            }
        }
        return null
    }

    private fun legacyFile(fileName: String): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
}
