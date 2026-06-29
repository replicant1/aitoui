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

    @Query("SELECT * FROM medication_formats ORDER BY brandName COLLATE NOCASE")
    fun getAll(): Flow<List<MedicationFormatEntity>>
}
