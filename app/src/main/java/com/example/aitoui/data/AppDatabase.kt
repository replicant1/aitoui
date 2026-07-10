package com.example.aitoui.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MedicationEntity::class,
        DispensableUnitEntity::class,
        ScriptEntity::class,
        DispensationEntity::class,
        DailyScheduleEntity::class,
    ],
    version = 17,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun dispensableUnitDao(): DispensableUnitDao
    abstract fun scriptDao(): ScriptDao
    abstract fun dispensationDao(): DispensationDao
    abstract fun dailyScheduleDao(): DailyScheduleDao
}
