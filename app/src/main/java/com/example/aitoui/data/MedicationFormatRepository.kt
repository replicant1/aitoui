package com.example.aitoui.data

import kotlinx.coroutines.flow.Flow

class MedicationFormatRepository(private val dao: MedicationFormatDao) {

    /** Formats joined with their medication, for lists/dropdowns. */
    val formatsWithMedication: Flow<List<MedicationFormatDetails>> = dao.getAllWithMedication()

    /** Inserts a format and returns its new id. */
    suspend fun add(format: MedicationFormat): Long = dao.insert(format.toEntity())

    suspend fun count(): Int = dao.count()
}
