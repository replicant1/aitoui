package com.example.aitoui.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations. Register each in [com.example.aitoui.AitouiApp]'s database builder. A registered
 * migration is preferred over the destructive fallback, so upgrading across it preserves data.
 */

/** v22 added [ScriptEntity.instructions] (the script's directions text). */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scripts ADD COLUMN instructions TEXT NOT NULL DEFAULT ''")
    }
}

/** v23 added [ScriptEntity.serialNo2] — a second serial slot (e.g. eRx token alongside the PBS number). */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scripts ADD COLUMN serialNo2 TEXT NOT NULL DEFAULT ''")
    }
}

/** All migrations, in order — spread into `addMigrations(*ALL_MIGRATIONS)`. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_21_22, MIGRATION_22_23)
