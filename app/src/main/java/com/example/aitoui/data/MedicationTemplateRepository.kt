package com.example.aitoui.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MedicationTemplateRepository(private val dao: MedicationTemplateDao) {

    val medicationTemplates: Flow<List<MedicationTemplate>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun add(template: MedicationTemplate) {
        dao.insert(template.toEntity())
    }
}
