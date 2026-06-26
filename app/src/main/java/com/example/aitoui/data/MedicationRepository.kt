package com.example.aitoui.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MedicationRepository(private val dao: MedicationDao) {

    val medications: Flow<List<Medication>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun add(medication: Medication) {
        dao.insert(medication.toEntity())
    }
}
