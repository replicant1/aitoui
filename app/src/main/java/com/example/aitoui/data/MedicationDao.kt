package com.example.aitoui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    /** Returns the auto-generated row id of the inserted medication. */
    @Insert
    suspend fun insert(entity: MedicationEntity): Long

    @Query("SELECT COUNT(*) FROM medications")
    suspend fun count(): Int

    @Query("SELECT * FROM medications ORDER BY brandName COLLATE NOCASE")
    fun getAll(): Flow<List<MedicationEntity>>
}
