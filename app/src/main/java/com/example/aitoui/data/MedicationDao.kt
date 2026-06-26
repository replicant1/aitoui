package com.example.aitoui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Insert
    suspend fun insert(entity: MedicationEntity)

    @Query("SELECT * FROM medications ORDER BY brandName COLLATE NOCASE")
    fun getAll(): Flow<List<MedicationEntity>>
}
