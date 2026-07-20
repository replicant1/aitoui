package com.example.aitoui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface InHandDao {
    @Query(
        """
        SELECT ih.id AS id, ih.dispensableUnitId AS dispensableUnitId,
               du.medicationId AS medicationId, m.brandName AS brandName, ih.quantity AS quantity
        FROM in_hand ih
        JOIN dispensable_units du ON du.id = ih.dispensableUnitId
        JOIN medications m ON m.id = du.medicationId
        ORDER BY m.brandName COLLATE NOCASE
        """
    )
    suspend fun getAllWithMedication(): List<InHandDetails>

    /** Reactive variant of [getAllWithMedication], for screens that observe in-hand changes live. */
    @Query(
        """
        SELECT ih.id AS id, ih.dispensableUnitId AS dispensableUnitId,
               du.medicationId AS medicationId, m.brandName AS brandName, ih.quantity AS quantity
        FROM in_hand ih
        JOIN dispensable_units du ON du.id = ih.dispensableUnitId
        JOIN medications m ON m.id = du.medicationId
        ORDER BY m.brandName COLLATE NOCASE
        """
    )
    fun getAllWithMedicationFlow(): Flow<List<InHandDetails>>

    @Insert
    suspend fun insert(entity: InHandEntity): Long

    @Insert
    suspend fun insertAll(entities: List<InHandEntity>)

    @Query("DELETE FROM in_hand")
    suspend fun clear()

    @Query("UPDATE in_hand SET quantity = quantity + :delta WHERE dispensableUnitId = :dispensableUnitId")
    suspend fun incrementQuantity(dispensableUnitId: Long, delta: Double): Int

    /** Overwrites the single [in_hand_date] row with the date the figures were gathered. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setDate(entity: InHandDateEntity)

    /** The gathered date (UTC epoch millis at start of day), or null if nothing has been saved yet. */
    @Query("SELECT gatheredAtMillis FROM in_hand_date WHERE id = 0")
    fun getDateFlow(): Flow<Long?>

    /**
     * Replaces the entire in-hand list with [entities] and stamps [gatheredAtMillis] as the date the
     * figures were gathered — atomically, so the list and its date can never diverge.
     */
    @Transaction
    suspend fun replaceAll(entities: List<InHandEntity>, gatheredAtMillis: Long) {
        clear()
        insertAll(entities)
        setDate(InHandDateEntity(gatheredAtMillis = gatheredAtMillis))
    }

    /** Adds [quantity] tablets of [dispensableUnitId] to the in-hand total (increment, or insert if new). */
    @Transaction
    suspend fun addTablets(dispensableUnitId: Long, quantity: Double) {
        if (incrementQuantity(dispensableUnitId, quantity) == 0) {
            insert(InHandEntity(dispensableUnitId = dispensableUnitId, quantity = quantity))
        }
    }
}
