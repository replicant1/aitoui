package com.example.aitoui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyScheduleDao {
    @Query(
        """
        SELECT ds.id AS id, ds.medicationId AS medicationId,
               m.brandName AS brandName, ds.quantity AS quantity
        FROM daily_schedule ds
        JOIN medications m ON m.id = ds.medicationId
        ORDER BY m.brandName COLLATE NOCASE
        """
    )
    suspend fun getAllWithMedication(): List<DailyScheduleDetails>

    /** Reactive variant of [getAllWithMedication], for screens that recompute on schedule changes. */
    @Query(
        """
        SELECT ds.id AS id, ds.medicationId AS medicationId,
               m.brandName AS brandName, ds.quantity AS quantity
        FROM daily_schedule ds
        JOIN medications m ON m.id = ds.medicationId
        ORDER BY m.brandName COLLATE NOCASE
        """
    )
    fun getAllWithMedicationFlow(): Flow<List<DailyScheduleDetails>>

    @Insert
    suspend fun insertAll(entities: List<DailyScheduleEntity>)

    @Query("DELETE FROM daily_schedule")
    suspend fun clear()

    /** Replaces the entire table with [entities] atomically. */
    @Transaction
    suspend fun replaceAll(entities: List<DailyScheduleEntity>) {
        clear()
        insertAll(entities)
    }
}
