package com.example.aitoui.data

import kotlinx.coroutines.flow.Flow

class InHandRepository(private val dao: InHandDao) {

    /** The current in-hand tablets (reactive), joined with medication brand names. */
    val inHand: Flow<List<InHandDetails>> = dao.getAllWithMedicationFlow()

    /** The date the in-hand figures were gathered (UTC epoch millis at start of day), or null if never saved. */
    val gatheredDate: Flow<Long?> = dao.getDateFlow()

    /** The current in-hand tablets, joined with medication brand names. */
    suspend fun getAll(): List<InHandDetails> = dao.getAllWithMedication()

    /** Replaces the whole in-hand list with [items], stamping [gatheredAtMillis] as the gathered date. */
    suspend fun save(items: List<InHandItem>, gatheredAtMillis: Long) =
        dao.replaceAll(items.map { it.toEntity() }, gatheredAtMillis)

    /** Adds [quantity] tablets of [dispensableUnitId] to the in-hand total (called when a dispensation occurs). */
    suspend fun addTablets(dispensableUnitId: Long, quantity: Double) = dao.addTablets(dispensableUnitId, quantity)
}
