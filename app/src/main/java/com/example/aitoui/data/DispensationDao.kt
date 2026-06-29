package com.example.aitoui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DispensationDao {
    @Insert
    suspend fun insert(entity: DispensationEntity): Long

    @Query("SELECT * FROM dispensations ORDER BY dispensedAtMillis DESC")
    fun getAll(): Flow<List<DispensationEntity>>
}
