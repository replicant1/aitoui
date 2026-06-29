package com.example.aitoui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationFormatDao {
    @Insert
    suspend fun insert(entity: MedicationFormatEntity)

    @Query("SELECT * FROM medication_formats ORDER BY brandName COLLATE NOCASE")
    fun getAll(): Flow<List<MedicationFormatEntity>>
}
