package com.example.aitoui.backup

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pure helpers for the backup zip's file name — the default, the .zip normalisation, and validation. */
object BackupFileName {

    /** The suggested name, e.g. "pxtx-21072026-db26.zip": brand, today's date (ddMMyyyy), and schema version. */
    fun default(schemaVersion: Int, nowMillis: Long): String {
        val date = SimpleDateFormat("ddMMyyyy", Locale.getDefault()).format(Date(nowMillis))
        return "pxtx-$date-db$schemaVersion.zip"
    }

    /** Trims [raw] and ensures it ends with a ".zip" extension (appending one if absent). */
    fun ensureZip(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.endsWith(".zip", ignoreCase = true)) trimmed else "$trimmed.zip"
    }

    /** Whether [raw] is an acceptable backup name: a non-blank base and no path separators. */
    fun isValid(raw: String): Boolean {
        val name = ensureZip(raw)
        val base = name.dropLast(".zip".length)
        return base.isNotBlank() && '/' !in name && '\\' !in name
    }
}
