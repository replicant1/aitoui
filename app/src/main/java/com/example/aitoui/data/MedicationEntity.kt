package com.example.aitoui.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brandName: String,
    val activeIngredient: String,
)

fun MedicationEntity.toDomain(): Medication = Medication(
    id = id,
    brandName = brandName,
    activeIngredient = activeIngredient,
)

fun Medication.toEntity(): MedicationEntity = MedicationEntity(
    id = id,
    brandName = brandName,
    activeIngredient = activeIngredient,
)
