package com.example.aitoui.dispensableunit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.DispensableUnitRepository
import com.example.aitoui.image.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** State for the Dispensable Units list — one [DispensableUnitDetails] per dispensable_units row. */
data class DispensableUnitsState(
    val units: List<DispensableUnitDetails> = emptyList(),
    /** Unit awaiting the user's confirmation to delete it, if any. */
    val pendingDeleteUnitId: Long? = null,
) {
    val pendingDeleteUnit: DispensableUnitDetails?
        get() = units.firstOrNull { it.formatId == pendingDeleteUnitId }
}

sealed interface DispensableUnitsAction {
    /** The user tapped the delete (cross) icon on a dispensable unit row. */
    data class DeleteTapped(val id: Long) : DispensableUnitsAction
    /** Confirm deleting the pending unit. */
    data object ConfirmDelete : DispensableUnitsAction
    /** Dismiss the delete confirmation without deleting. */
    data object CancelDelete : DispensableUnitsAction

    /**
     * The camera returned a full-res capture in [source] for the unit with [id]. The image is
     * downscaled and stored, any previous photo is discarded, and the filename is persisted.
     */
    data class PhotoCaptured(val id: Long, val source: File) : DispensableUnitsAction
    /** Remove the tablet photo from the unit with [id]. */
    data class PhotoRemoved(val id: Long) : DispensableUnitsAction
}

class DispensableUnitsViewModel(
    private val repository: DispensableUnitRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(DispensableUnitsState())
    val state: StateFlow<DispensableUnitsState> = _state.asStateFlow()

    init {
        repository.formatsWithMedication
            .onEach { units ->
                // Alphabetical (case-insensitive) by brand name — independent of the source query.
                val sorted = units.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.brandName })
                _state.update { current ->
                    // Drop the pending delete if that unit no longer exists.
                    current.copy(
                        units = sorted,
                        pendingDeleteUnitId = current.pendingDeleteUnitId
                            ?.takeIf { id -> sorted.any { it.formatId == id } },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: DispensableUnitsAction) {
        when (action) {
            is DispensableUnitsAction.DeleteTapped ->
                _state.update { it.copy(pendingDeleteUnitId = action.id) }

            DispensableUnitsAction.CancelDelete ->
                _state.update { it.copy(pendingDeleteUnitId = null) }

            DispensableUnitsAction.ConfirmDelete -> deleteUnit()

            is DispensableUnitsAction.PhotoCaptured -> savePhoto(action.id, action.source)

            is DispensableUnitsAction.PhotoRemoved -> removePhoto(action.id)
        }
    }

    /** Deletes the pending unit (its scripts/dispensations cascade). The list updates reactively. */
    private fun deleteUnit() {
        val unit = _state.value.pendingDeleteUnit ?: return
        viewModelScope.launch {
            repository.deleteById(unit.formatId)
            ImageStore.delete(appContext, unit.imagePath)
            _state.update { it.copy(pendingDeleteUnitId = null) }
        }
    }

    /** Stores the downscaled capture, discards any prior photo, and persists the new filename. */
    private fun savePhoto(id: Long, source: File) {
        val previous = _state.value.units.firstOrNull { it.formatId == id }?.imagePath
        viewModelScope.launch {
            val fileName = withContext(Dispatchers.IO) {
                ImageStore.saveTabletPhoto(appContext, source)
            }
            repository.setImagePath(id, fileName)
            ImageStore.delete(appContext, previous)
        }
    }

    /** Clears the unit's photo filename and deletes the stored image. */
    private fun removePhoto(id: Long) {
        val previous = _state.value.units.firstOrNull { it.formatId == id }?.imagePath
        viewModelScope.launch {
            repository.setImagePath(id, null)
            ImageStore.delete(appContext, previous)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                DispensableUnitsViewModel(app.dispensableUnitRepository, app)
            }
        }
    }
}
