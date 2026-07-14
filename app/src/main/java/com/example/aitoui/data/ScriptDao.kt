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

    /** Deletes the script with [id]. Its dispensations cascade-delete via the foreign key. */
    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM scripts ORDER BY validToMillis")
    fun getAll(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun getById(id: Long): ScriptEntity?

    @Query(
        """
        SELECT s.id AS scriptId, s.dispensableUnitId AS dispensableUnitId,
               du.medicationId AS medicationId,
               m.brandName AS brandName, m.activeIngredient AS activeIngredient,
               du.dosePerTablet AS dosePerTablet, du.tabletsPerUnit AS tabletsPerUnit,
               COALESCE((SELECT SUM(d.number) FROM dispensations d WHERE d.scriptId = s.id), 0) AS dispensed,
               s.repeats AS repeats, s.dateOfIssue AS dateOfIssue, s.instructions AS instructions
        FROM scripts s
        JOIN dispensable_units du ON du.id = s.dispensableUnitId
        JOIN medications m ON m.id = du.medicationId
        ORDER BY m.brandName COLLATE NOCASE, s.id
        """
    )
    fun getAllWithDetails(): Flow<List<ScriptDetails>>
}
