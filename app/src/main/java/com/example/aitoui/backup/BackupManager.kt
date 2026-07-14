package com.example.aitoui.backup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reads and writes a pxtx.zip backup: the Room database file plus the tablet-photo directories, with a
 * [BackupManifest] recording the schema version. All work runs on [Dispatchers.IO].
 *
 * Zip layout:
 * ```
 * manifest.json                  { "schemaVersion": 21, "createdAtMillis": ... }
 * database/aitoui.db
 * images/unit_images/<file>      thumbnails
 * images/unit_images_full/<file> full-res
 * ```
 * The caller is responsible for checkpointing the WAL before [writeTo] and for closing the database
 * before [restoreFrom] (the app restarts afterwards).
 */
object BackupManager {

    private const val MANIFEST_ENTRY = "manifest.json"
    private const val DB_DIR = "database"
    private const val IMAGES_DIR = "images"
    // Must match ImageStore's internal-storage directory names.
    private const val THUMBS_DIR = "unit_images"
    private const val FULL_DIR = "unit_images_full"

    private val json = Json { ignoreUnknownKeys = true }

    /** Writes a complete backup of [dbName] + image dirs to [out], stamped with [schemaVersion]. */
    suspend fun writeTo(
        context: Context,
        out: OutputStream,
        dbName: String,
        schemaVersion: Int,
        nowMillis: Long,
    ) = withContext(Dispatchers.IO) {
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            val manifest = json.encodeToString(BackupManifest(schemaVersion, nowMillis))
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifest.toByteArray())
            zip.closeEntry()

            val dbFile = context.getDatabasePath(dbName)
            if (dbFile.exists()) addFile(zip, dbFile, "$DB_DIR/${dbFile.name}")

            addDir(zip, File(context.filesDir, THUMBS_DIR), "$IMAGES_DIR/$THUMBS_DIR")
            addDir(zip, File(context.filesDir, FULL_DIR), "$IMAGES_DIR/$FULL_DIR")
        }
    }

    /** Reads only the manifest's schema version from [input], or null if the zip has no valid manifest. */
    suspend fun peekSchemaVersion(input: InputStream): Int? = withContext(Dispatchers.IO) {
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == MANIFEST_ENTRY) {
                    val text = zip.readBytes().decodeToString()
                    return@withContext runCatching {
                        json.decodeFromString<BackupManifest>(text).schemaVersion
                    }.getOrNull()
                }
                entry = zip.nextEntry
            }
            null
        }
    }

    /**
     * Overwrites the current database and image directories with the contents of [input]. Extracts to a
     * cache staging dir first so a mid-stream failure can't leave a half-written database. The caller must
     * have closed the database and already validated the schema version.
     */
    suspend fun restoreFrom(context: Context, input: InputStream, dbName: String) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore_$dbName").apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = File(staging, entry.name)
                        // Guard against zip-slip (entry names escaping the staging dir).
                        if (!outFile.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                            throw IOException("Illegal zip entry: ${entry.name}")
                        }
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zip.copyTo(it) }
                    }
                    entry = zip.nextEntry
                }
            }

            val stagedDb = File(staging, "$DB_DIR/$dbName")
            if (stagedDb.exists()) {
                val dbFile = context.getDatabasePath(dbName)
                dbFile.parentFile?.mkdirs()
                // Drop any stale WAL/SHM so the restored .db is opened cleanly.
                File(dbFile.parentFile, "$dbName-wal").delete()
                File(dbFile.parentFile, "$dbName-shm").delete()
                stagedDb.copyTo(dbFile, overwrite = true)
            }

            replaceDir(File(staging, "$IMAGES_DIR/$THUMBS_DIR"), File(context.filesDir, THUMBS_DIR))
            replaceDir(File(staging, "$IMAGES_DIR/$FULL_DIR"), File(context.filesDir, FULL_DIR))
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun addDir(zip: ZipOutputStream, dir: File, entryPrefix: String) {
        dir.listFiles()?.forEach { f -> if (f.isFile) addFile(zip, f, "$entryPrefix/${f.name}") }
    }

    /** Replaces [target]'s contents with [staged]'s files (empties [target] if [staged] is absent). */
    private fun replaceDir(staged: File, target: File) {
        target.deleteRecursively()
        target.mkdirs()
        staged.listFiles()?.forEach { it.copyTo(File(target, it.name), overwrite = true) }
    }
}
