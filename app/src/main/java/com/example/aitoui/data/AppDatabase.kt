package com.example.aitoui.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MedicationEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
}
