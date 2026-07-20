package com.example.aitoui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.alerts.AttentionMessage
import com.example.aitoui.alerts.attentionMessages
import com.example.aitoui.alerts.medicationSupplies
import com.example.aitoui.backup.BackupManager
import com.example.aitoui.backup.DownloadsBackupStore
import com.example.aitoui.data.DATABASE_SCHEMA_VERSION
import com.example.aitoui.data.DatabaseDumper
import com.example.aitoui.data.inHandDaysElapsed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screen state for the main menu — everything here drives the backup Save/Load dialogs. */
data class MainState(
    /** A Save or Load is in progress; buttons are disabled and a spinner shows. */
    val busy: Boolean = false,
    /** The backup file the user picked (via the system file picker) and must confirm loading, if any. */
    val pendingLoadUri: String? = null,
    /** A success/info message to show (e.g. "Saved to Downloads/pxtx.zip"). */
    val message: String? = null,
    /** An error message to show (invalid/newer-version backup, IO failure). */
    val error: String? = null,
    /** A restore finished; the screen should restart the app so the new database is opened. */
    val restoreComplete: Boolean = false,
    /** Attention messages (running low, no scripts, …) shown at the bottom of the menu; empty = hidden. */
    val messages: List<AttentionMessage> = emptyList(),
)

/** User intents from the main menu's backup buttons/dialogs. */
sealed interface MainAction {
    data object SaveTapped : MainAction
    /** The user chose a backup file in the system file picker; [uriString] identifies it. */
    data class LoadFilePicked(val uriString: String) : MainAction
    data object ConfirmLoad : MainAction
    data object CancelLoad : MainAction
    data object DismissMessage : MainAction
}

/** Raised for expected, user-facing backup problems so the message is shown verbatim. */
private class BackupException(message: String) : Exception(message)

class MainViewModel(private val app: AitouiApp) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    init {
        // Derive the attention messages from the same inventory data the Inventory screen uses.
        combine(
            app.dispensableUnitRepository.formatsWithMedication,
            app.inHandRepository.inHand,
            app.dailyScheduleRepository.dailySchedule,
            app.scriptRepository.scriptsWithDetails,
            app.inHandRepository.gatheredDate,
        ) { formats, inHand, schedule, scripts, gatheredDate ->
            val dailyByMedication = schedule
                .groupBy { it.medicationId }
                .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
            val inHandByMedication = inHand
                .groupBy { it.medicationId }
                .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
            val supplies = medicationSupplies(
                units = formats,
                scripts = scripts,
                dailyByMedication = dailyByMedication,
                inHandByMedication = inHandByMedication,
                daysSinceGathered = inHandDaysElapsed(gatheredDate, System.currentTimeMillis()),
            )
            attentionMessages(supplies)
        }
            .onEach { messages -> _state.update { it.copy(messages = messages) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.SaveTapped -> save()
            is MainAction.LoadFilePicked -> _state.update { it.copy(pendingLoadUri = action.uriString) }
            MainAction.ConfirmLoad -> confirmLoad()
            MainAction.CancelLoad -> _state.update { it.copy(pendingLoadUri = null) }
            MainAction.DismissMessage -> _state.update { it.copy(message = null, error = null) }
        }
    }

    /** Writes the database + images to Downloads/pxtx.zip, overwriting any existing backup. */
    private fun save() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    app.checkpointDatabase()
                    DownloadsBackupStore.openOutput(app).use { out ->
                        BackupManager.writeTo(
                            context = app,
                            out = out,
                            dbName = app.databaseName,
                            schemaVersion = DATABASE_SCHEMA_VERSION,
                            nowMillis = System.currentTimeMillis(),
                        )
                    }
                }
            }
            _state.update {
                result.fold(
                    onSuccess = { _ -> it.copy(busy = false, message = "Saved to Downloads/${DownloadsBackupStore.FILE_NAME}") },
                    onFailure = { e -> it.copy(busy = false, error = "Couldn't save backup: ${e.message}") },
                )
            }
        }
    }

    /** Validates the picked backup's schema version, then overwrites the current data with it. */
    private fun confirmLoad() {
        val uriString = _state.value.pendingLoadUri ?: return
        if (_state.value.busy) return
        _state.update { it.copy(pendingLoadUri = null, busy = true) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val uri = Uri.parse(uriString)
                    // Read the backup's schema version from its own stream (the zip isn't seekable).
                    val version = (app.contentResolver.openInputStream(uri)
                        ?: throw BackupException("Couldn't open the selected file."))
                        .use { BackupManager.peekSchemaVersion(it) }
                        ?: throw BackupException("The selected file is not a valid PxTx backup.")

                    if (version > DATABASE_SCHEMA_VERSION) {
                        throw BackupException(
                            "This backup was created by a newer version of PxTx and stores schema " +
                                "version $version. This app can only read up to schema version " +
                                "$DATABASE_SCHEMA_VERSION. Update PxTx to a version that supports schema " +
                                "version $version to load this backup.",
                        )
                    }

                    // Version is acceptable (== migrates trivially, < is migrated by Room on next open).
                    // Close the DB, then overwrite its files from a fresh stream. The app restarts next.
                    app.closeDatabase()
                    (app.contentResolver.openInputStream(uri)
                        ?: throw BackupException("The selected file could not be reopened for restore."))
                        .use { BackupManager.restoreFrom(app, it, app.databaseName) }
                }
            }
            result.fold(
                onSuccess = { _state.update { it.copy(busy = false, restoreComplete = true) } },
                onFailure = { e ->
                    val msg = (e as? BackupException)?.message ?: "Couldn't load backup: ${e.message}"
                    _state.update { it.copy(busy = false, error = msg) }
                },
            )
        }
    }

    /** Dumps the current database contents to logcat as ASCII tables (see [DatabaseDumper]). */
    fun logDatabase() {
        viewModelScope.launch {
            val dump = DatabaseDumper.dump(
                medicationRepository = app.medicationRepository,
                formatRepository = app.dispensableUnitRepository,
                scriptRepository = app.scriptRepository,
                dispensationRepository = app.dispensationRepository,
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
                MainViewModel(app)
            }
        }
    }
}
