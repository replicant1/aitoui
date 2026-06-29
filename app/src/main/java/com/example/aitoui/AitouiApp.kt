package com.example.aitoui

import android.app.Application
import androidx.room.Room
import com.example.aitoui.data.AppDatabase
import com.example.aitoui.data.MedicationFormatRepository
import com.example.aitoui.data.ScriptRepository

/**
 * Application that owns the singleton data layer. ViewModels reach the repository via a
 * [androidx.lifecycle.viewmodel.viewModelFactory] using the APPLICATION_KEY from CreationExtras.
 */
class AitouiApp : Application() {

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "aitoui.db")
            // Dev app: schema changes recreate the DB rather than requiring hand-written migrations.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val medicationFormatRepository: MedicationFormatRepository by lazy {
        MedicationFormatRepository(database.medicationFormatDao())
    }

    val scriptRepository: ScriptRepository by lazy {
        ScriptRepository(database.scriptDao())
    }
}
