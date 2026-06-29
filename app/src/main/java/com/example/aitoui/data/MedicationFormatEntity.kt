package com.example.aitoui.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_formats")
data class MedicationFormatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brandName: String,
    val activeIngredient: String,
    val dosePerTablet: String,
    val tabletsPerBox: String,
)

fun MedicationFormatEntity.toDomain(): MedicationFormat = MedicationFormat(
    id = id,
    brandName = brandName,
    activeIngredient = activeIngredient,
    dosePerTablet = dosePerTablet,
    tabletsPerBox = tabletsPerBox,
)

fun MedicationFormat.toEntity(): MedicationFormatEntity = MedicationFormatEntity(
    id = id,
    brandName = brandName,
    activeIngredient = activeIngredient,
    dosePerTablet = dosePerTablet,
    tabletsPerBox = tabletsPerBox,
)
