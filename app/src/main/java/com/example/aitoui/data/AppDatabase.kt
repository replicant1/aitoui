package com.example.aitoui.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MedicationFormatEntity::class, ScriptEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationFormatDao(): MedicationFormatDao
    abstract fun scriptDao(): ScriptDao
}
