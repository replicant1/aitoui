package com.example.aitoui

import android.app.Application
import androidx.room.Room
import com.example.aitoui.data.AppDatabase
import com.example.aitoui.data.MedicationRepository

/**
 * Application that owns the singleton data layer. ViewModels reach the repository via a
 * [androidx.lifecycle.viewmodel.viewModelFactory] using the APPLICATION_KEY from CreationExtras.
 */
class AitouiApp : Application() {

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "aitoui.db").build()
    }

    val medicationRepository: MedicationRepository by lazy {
        MedicationRepository(database.medicationDao())
    }
}
