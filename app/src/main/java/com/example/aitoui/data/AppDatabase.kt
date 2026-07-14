package com.example.aitoui.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The current Room schema version. Single source of truth: used both in the [Database] annotation below
 * and stamped into backup zips (see the backup package) so a restore can compare versions.
 */
const val DATABASE_SCHEMA_VERSION = 21

@Database(
    entities = [
        MedicationEntity::class,
        DispensableUnitEntity::class,
        ScriptEntity::class,
        DispensationEntity::class,
        DailyScheduleEntity::class,
        InHandEntity::class,
        InHandDateEntity::class,
    ],
    version = DATABASE_SCHEMA_VERSION,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun dispensableUnitDao(): DispensableUnitDao
    abstract fun scriptDao(): ScriptDao
    abstract fun dispensationDao(): DispensationDao
    abstract fun dailyScheduleDao(): DailyScheduleDao
    abstract fun inHandDao(): InHandDao
}
