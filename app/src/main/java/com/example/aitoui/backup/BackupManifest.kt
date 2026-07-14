package com.example.aitoui.backup

import kotlinx.serialization.Serializable

/**
 * Metadata stored as `manifest.json` at the root of a pxtx.zip backup. [schemaVersion] is the Room
 * schema version the database was written at (see `DATABASE_SCHEMA_VERSION`); a restore compares it
 * against the running app's version to decide whether to migrate or reject the backup.
 */
@Serializable
data class BackupManifest(
    val schemaVersion: Int,
    val createdAtMillis: Long,
)
