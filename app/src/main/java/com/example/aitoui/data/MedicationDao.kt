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

    @Query("SELECT * FROM medications ORDER BY id")
    suspend fun getAllNow(): List<MedicationEntity>

    @Query(
        "UPDATE medications " +
            "SET brandName = :brandName, activeIngredient = :activeIngredient " +
            "WHERE id = :id"
    )
    suspend fun updateNames(id: Long, brandName: String, activeIngredient: String)

    /** Deletes the medication with [id]. Its units, scripts and dispensations cascade-delete. */
    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: Long)
}
