package com.example.aitoui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DispensableUnitDao {
    /** Returns the auto-generated row id of the inserted format. */
    @Insert
    suspend fun insert(entity: DispensableUnitEntity): Long

    @Query("SELECT COUNT(*) FROM dispensable_units")
    suspend fun count(): Int

    @Query(
        """
        SELECT f.id AS formatId, f.medicationId AS medicationId,
               m.brandName AS brandName, m.activeIngredient AS activeIngredient,
               f.dosePerTablet AS dosePerTablet, f.tabletsPerUnit AS tabletsPerUnit
        FROM dispensable_units f
        JOIN medications m ON m.id = f.medicationId
        ORDER BY m.brandName COLLATE NOCASE, f.dosePerTablet
        """
    )
    fun getAllWithMedication(): Flow<List<DispensableUnitDetails>>

    /** Deletes the dispensable unit with [id]. Its scripts and dispensations cascade-delete. */
    @Query("DELETE FROM dispensable_units WHERE id = :id")
    suspend fun deleteById(id: Long)
}
