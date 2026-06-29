package com.example.aitoui.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MedicationTemplateEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationTemplateDao(): MedicationTemplateDao
}
