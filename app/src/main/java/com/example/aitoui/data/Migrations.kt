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

/**
 * v24 added [MedicationEntity.requiresPrescription] — whether the medication needs a prescription.
 * Existing medications default to requiring one (1); CARTIA is the one exception and is set to 0.
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medications ADD COLUMN requiresPrescription INTEGER NOT NULL DEFAULT 1")
        db.execSQL("UPDATE medications SET requiresPrescription = 0 WHERE UPPER(brandName) = 'CARTIA'")
    }
}

/**
 * v25 re-keys [InHandEntity] from `medicationId` to `dispensableUnitId` (a FK to `dispensable_units`), so
 * in-hand stock is tracked per dispensable unit (dose/format) rather than per medication.
 *
 * SQLite can't re-point a column's foreign key in place, so the table is recreated. Existing rows are
 * back-filled by mapping each row's medication to that medication's dispensable unit (the lowest id when a
 * medication somehow has more than one). A row whose medication has no dispensable unit can't reference one,
 * so it is dropped — it could never have been counted against a unit anyway.
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE in_hand_new (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "dispensableUnitId INTEGER NOT NULL, " +
                "quantity REAL NOT NULL, " +
                "FOREIGN KEY(dispensableUnitId) REFERENCES dispensable_units(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "INSERT INTO in_hand_new (id, dispensableUnitId, quantity) " +
                "SELECT ih.id, " +
                "(SELECT du.id FROM dispensable_units du WHERE du.medicationId = ih.medicationId " +
                "ORDER BY du.id LIMIT 1), ih.quantity " +
                "FROM in_hand ih " +
                "WHERE EXISTS (SELECT 1 FROM dispensable_units du WHERE du.medicationId = ih.medicationId)",
        )
        db.execSQL("DROP TABLE in_hand")
        db.execSQL("ALTER TABLE in_hand_new RENAME TO in_hand")
        db.execSQL("CREATE INDEX index_in_hand_dispensableUnitId ON in_hand (dispensableUnitId)")
    }
}

/**
 * v26 re-keys [DailyScheduleEntity] from `medicationId` to `dispensableUnitId` (a FK to `dispensable_units`),
 * the same change [MIGRATION_24_25] made to `in_hand`, so the daily schedule is kept per dispensable unit
 * (dose/format). The table is recreated (SQLite can't re-point a column FK) and back-filled by mapping each
 * row's medication to that medication's dispensable unit (lowest id if >1); a row whose medication has no
 * unit is dropped.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE daily_schedule_new (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "dispensableUnitId INTEGER NOT NULL, " +
                "quantity REAL NOT NULL, " +
                "FOREIGN KEY(dispensableUnitId) REFERENCES dispensable_units(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "INSERT INTO daily_schedule_new (id, dispensableUnitId, quantity) " +
                "SELECT ds.id, " +
                "(SELECT du.id FROM dispensable_units du WHERE du.medicationId = ds.medicationId " +
                "ORDER BY du.id LIMIT 1), ds.quantity " +
                "FROM daily_schedule ds " +
                "WHERE EXISTS (SELECT 1 FROM dispensable_units du WHERE du.medicationId = ds.medicationId)",
        )
        db.execSQL("DROP TABLE daily_schedule")
        db.execSQL("ALTER TABLE daily_schedule_new RENAME TO daily_schedule")
        db.execSQL("CREATE INDEX index_daily_schedule_dispensableUnitId ON daily_schedule (dispensableUnitId)")
    }
}

/** v27 added [DispensableUnitEntity.doseUnit] to support multiple dose units (mg, g, IU, mL, mcg). */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE dispensable_units ADD COLUMN doseUnit TEXT NOT NULL DEFAULT 'mg'")
    }
}

/** All migrations, in order — spread into `addMigrations(*ALL_MIGRATIONS)`. */
val ALL_MIGRATIONS =
    arrayOf(MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27)
