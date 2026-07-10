package com.example.aitoui.data

class InHandRepository(private val dao: InHandDao) {

    /** The current in-hand tablets, joined with medication brand names. */
    suspend fun getAll(): List<InHandDetails> = dao.getAllWithMedication()

    /** Replaces the whole in-hand list with [items]. */
    suspend fun save(items: List<InHandItem>) = dao.replaceAll(items.map { it.toEntity() })

    /** Adds [quantity] tablets of [medicationId] to the in-hand total (called when a dispensation occurs). */
    suspend fun addTablets(medicationId: Long, quantity: Double) = dao.addTablets(medicationId, quantity)
}
