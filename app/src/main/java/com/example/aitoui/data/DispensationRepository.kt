package com.example.aitoui.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DispensationRepository(private val dao: DispensationDao) {

    val dispensations: Flow<List<Dispensation>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun add(dispensation: Dispensation): Long = dao.insert(dispensation.toEntity())
}
