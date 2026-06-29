package com.example.aitoui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationTemplateDao {
    @Insert
    suspend fun insert(entity: MedicationTemplateEntity)

    @Query("SELECT * FROM medication_templates ORDER BY brandName COLLATE NOCASE")
    fun getAll(): Flow<List<MedicationTemplateEntity>>
}
