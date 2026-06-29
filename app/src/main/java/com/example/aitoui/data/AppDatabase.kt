package com.example.aitoui.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MedicationEntity::class, MedicationFormatEntity::class, ScriptEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationFormatDao(): MedicationFormatDao
    abstract fun scriptDao(): ScriptDao
}
