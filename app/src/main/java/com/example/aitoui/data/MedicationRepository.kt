package com.example.aitoui.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MedicationRepository(private val dao: MedicationDao) {

    val medications: Flow<List<Medication>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    /** Inserts a medication and returns its new id. */
    suspend fun add(medication: Medication): Long = dao.insert(medication.cleaned().toEntity())

    /** Rewrites legacy rows so medication names are trimmed, single-spaced, and title-cased. */
    suspend fun cleanseExistingMedicationNames() {
        dao.getAllNow().forEach { entity ->
            val cleaned = entity.toDomain().cleaned()
            if (cleaned.brandName != entity.brandName || cleaned.activeIngredient != entity.activeIngredient) {
                dao.updateNames(entity.id, cleaned.brandName, cleaned.activeIngredient)
            }
        }
    }

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun count(): Int = dao.count()
}
