package com.example.aitoui.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MedicationFormatRepository(private val dao: MedicationFormatDao) {

    val medicationFormats: Flow<List<MedicationFormat>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun add(template: MedicationFormat) {
        dao.insert(template.toEntity())
    }
}
