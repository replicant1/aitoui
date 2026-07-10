package com.example.aitoui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.data.DatabaseDumper
import com.example.aitoui.data.DispensationRepository
import com.example.aitoui.data.DispensableUnitRepository
import com.example.aitoui.data.MedicationRepository
import com.example.aitoui.data.ScriptRepository
import kotlinx.coroutines.launch

class MainViewModel(
    private val medicationRepository: MedicationRepository,
    private val formatRepository: DispensableUnitRepository,
    private val scriptRepository: ScriptRepository,
    private val dispensationRepository: DispensationRepository,
) : ViewModel() {

    /** Dumps the current database contents to logcat as ASCII tables (see [DatabaseDumper]). */
    fun logDatabase() {
        viewModelScope.launch {
            val dump = DatabaseDumper.dump(
                medicationRepository = medicationRepository,
                formatRepository = formatRepository,
                scriptRepository = scriptRepository,
                dispensationRepository = dispensationRepository,
            )
            // Log line-by-line: logcat truncates individual messages at ~4 KB, which would clip a
            // large single-message dump.
            Log.d(TAG, "===== DATABASE DUMP =====")
            dump.lineSequence().forEach { Log.d(TAG, it) }
        }
    }

    companion object {
        const val TAG = "DatabaseDump"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                MainViewModel(
                    medicationRepository = app.medicationRepository,
                    formatRepository = app.dispensableUnitRepository,
                    scriptRepository = app.scriptRepository,
                    dispensationRepository = app.dispensationRepository,
                )
            }
        }
    }
}
