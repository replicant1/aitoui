package com.example.aitoui

import android.app.Application
import androidx.room.Room
import com.example.aitoui.data.AppDatabase
import com.example.aitoui.data.DatabaseSeeder
import com.example.aitoui.data.MedicationFormatRepository
import com.example.aitoui.data.MedicationRepository
import com.example.aitoui.data.ScriptRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application that owns the singleton data layer. ViewModels reach the repository via a
 * [androidx.lifecycle.viewmodel.viewModelFactory] using the APPLICATION_KEY from CreationExtras.
 */
class AitouiApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "aitoui.db")
            // Dev app: schema changes recreate the DB rather than requiring hand-written migrations.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val medicationRepository: MedicationRepository by lazy {
        MedicationRepository(database.medicationDao())
    }

    val medicationFormatRepository: MedicationFormatRepository by lazy {
        MedicationFormatRepository(database.medicationFormatDao())
    }

    val scriptRepository: ScriptRepository by lazy {
        ScriptRepository(database.scriptDao())
    }

    override fun onCreate() {
        super.onCreate()
        // Debug-only: auto-populate the DB with dummy data on first run so the app can be exercised
        // without manual entry. Idempotent (seeds only when empty), so it never grows unbounded.
        if (BuildConfig.DEBUG) {
            applicationScope.launch {
                DatabaseSeeder.seedIfEmpty(
                    medicationRepository = medicationRepository,
                    formatRepository = medicationFormatRepository,
                    scriptRepository = scriptRepository,
                    nowMillis = System.currentTimeMillis(),
                )
            }
        }
    }
}
