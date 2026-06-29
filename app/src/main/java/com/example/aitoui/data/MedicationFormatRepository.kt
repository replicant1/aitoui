package com.example.aitoui.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MedicationFormatRepository(private val dao: MedicationFormatDao) {

    val medicationFormats: Flow<List<MedicationFormat>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    /** Inserts a format and returns its new id. */
    suspend fun add(format: MedicationFormat): Long = dao.insert(format.toEntity())

    suspend fun count(): Int = dao.count()
}
