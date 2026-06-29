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

    @Query("SELECT COUNT(*) FROM medication_formats")
    suspend fun count(): Int

    @Query(
        """
        SELECT f.id AS formatId, f.medicationId AS medicationId,
               m.brandName AS brandName, m.activeIngredient AS activeIngredient,
               f.dosePerTablet AS dosePerTablet, f.tabletsPerBox AS tabletsPerBox,
               COALESCE(s.dispensed, 0) AS dispensed,
               COALESCE(s.quantity, 0) AS quantity
        FROM medication_formats f
        JOIN medications m ON m.id = f.medicationId
        LEFT JOIN scripts s ON s.medicationFormatId = f.id
        GROUP BY f.id
        ORDER BY m.brandName COLLATE NOCASE, f.dosePerTablet
        """
    )
    fun getAllWithMedication(): Flow<List<MedicationFormatDetails>>
}
