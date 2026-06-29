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

    @Query(
        """
        SELECT s.id AS scriptId, s.dispensableUnitId AS dispensableUnitId,
               m.brandName AS brandName, du.dosePerTablet AS dosePerTablet,
               COALESCE((SELECT SUM(d.number) FROM dispensations d WHERE d.scriptId = s.id), 0) AS dispensed,
               s.quantity AS quantity
        FROM scripts s
        JOIN dispensable_units du ON du.id = s.dispensableUnitId
        JOIN medications m ON m.id = du.medicationId
        ORDER BY m.brandName COLLATE NOCASE, s.id
        """
    )
    fun getAllWithDetails(): Flow<List<ScriptDetails>>
}
