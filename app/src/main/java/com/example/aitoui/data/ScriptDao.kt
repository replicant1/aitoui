package com.example.aitoui.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Insert
    suspend fun insert(entity: ScriptEntity): Long

    @Update
    suspend fun update(entity: ScriptEntity)

    @Delete
    suspend fun delete(entity: ScriptEntity)

    @Query("SELECT * FROM scripts ORDER BY validToMillis")
    fun getAll(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun getById(id: Long): ScriptEntity?
}
