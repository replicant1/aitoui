package com.example.aitoui.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MedicationRepository(private val dao: MedicationDao) {

    val medications: Flow<List<Medication>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    /** Inserts a medication and returns its new id. */
    suspend fun add(medication: Medication): Long = dao.insert(medication.toEntity())

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun count(): Int = dao.count()
}
