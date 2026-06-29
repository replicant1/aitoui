package com.example.aitoui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationFormatDao {
    /** Returns the auto-generated row id of the inserted format. */
    @Insert
    suspend fun insert(entity: MedicationFormatEntity): Long

    @Query("SELECT COUNT(*) FROM dispensable_units")
    suspend fun count(): Int

    @Query(
        """
        SELECT f.id AS formatId, f.medicationId AS medicationId,
               m.brandName AS brandName, m.activeIngredient AS activeIngredient,
               f.dosePerTablet AS dosePerTablet, f.tabletsPerBox AS tabletsPerBox
        FROM dispensable_units f
        JOIN medications m ON m.id = f.medicationId
        ORDER BY m.brandName COLLATE NOCASE, f.dosePerTablet
        """
    )
    fun getAllWithMedication(): Flow<List<MedicationFormatDetails>>
}
