package com.example.aitoui

import android.app.Application
import androidx.room.Room
import com.example.aitoui.data.ALL_MIGRATIONS
import com.example.aitoui.data.AppDatabase
import com.example.aitoui.data.DailyScheduleRepository
import com.example.aitoui.data.DatabaseSeeder
import com.example.aitoui.data.DispensationRepository
import com.example.aitoui.data.InHandRepository
import com.example.aitoui.data.DispensableUnitRepository
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
            // Hand-written migrations preserve data across the versions they cover (see Migrations.kt).
            .addMigrations(*ALL_MIGRATIONS)
            // Fallback for any version jump without a migration (recreates rather than crashing).
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val medicationRepository: MedicationRepository by lazy {
        MedicationRepository(database.medicationDao())
    }

    val dispensableUnitRepository: DispensableUnitRepository by lazy {
        DispensableUnitRepository(database.dispensableUnitDao())
    }

    val scriptRepository: ScriptRepository by lazy {
        ScriptRepository(database.scriptDao())
    }

    val dispensationRepository: DispensationRepository by lazy {
        DispensationRepository(database.dispensationDao())
    }

    val dailyScheduleRepository: DailyScheduleRepository by lazy {
        DailyScheduleRepository(database.dailyScheduleDao())
    }

    val inHandRepository: InHandRepository by lazy {
        InHandRepository(database.inHandDao())
    }

    /** The on-disk name of the Room database (see [Room.databaseBuilder] above). */
    val databaseName: String get() = "aitoui.db"

    /**
     * Merges the write-ahead log into the main database file so a backup taken by copying just the `.db`
     * file is complete and consistent. Call before writing a backup.
     */
    fun checkpointDatabase() {
        database.query("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
    }

    /**
     * Closes the Room database so its files can be replaced during a restore. The app process is
     * restarted immediately afterwards, so the lazy singleton is never reopened in this process.
     */
    fun closeDatabase() {
        if (database.isOpen) database.close()
    }

    override fun onCreate() {
        super.onCreate()
        // Debug-only: auto-populate the DB with dummy data on first run so the app can be exercised
        // without manual entry. Idempotent (seeds only when empty), so it never grows unbounded.
        if (BuildConfig.DEBUG) {
            applicationScope.launch {
                DatabaseSeeder.seedIfEmpty(
                    medicationRepository = medicationRepository,
                    formatRepository = dispensableUnitRepository,
                    scriptRepository = scriptRepository,
                    dispensationRepository = dispensationRepository,
                    dailyScheduleRepository = dailyScheduleRepository,
                    inHandRepository = inHandRepository,
                    nowMillis = System.currentTimeMillis(),
                )
            }
        }
    }
}
