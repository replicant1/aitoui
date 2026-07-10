package com.example.aitoui.data

import kotlinx.coroutines.flow.Flow

class DispensableUnitRepository(private val dao: DispensableUnitDao) {

    /** Formats joined with their medication, for lists/dropdowns. */
    val formatsWithMedication: Flow<List<DispensableUnitDetails>> = dao.getAllWithMedication()

    /** Inserts a format and returns its new id. */
    suspend fun add(format: DispensableUnit): Long = dao.insert(format.toEntity())

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun count(): Int = dao.count()
}
