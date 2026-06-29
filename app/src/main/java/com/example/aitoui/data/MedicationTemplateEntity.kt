package com.example.aitoui.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_templates")
data class MedicationTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brandName: String,
    val activeIngredient: String,
    val dosePerTablet: String,
    val tabletsPerBox: String,
)

fun MedicationTemplateEntity.toDomain(): MedicationTemplate = MedicationTemplate(
    id = id,
    brandName = brandName,
    activeIngredient = activeIngredient,
    dosePerTablet = dosePerTablet,
    tabletsPerBox = tabletsPerBox,
)

fun MedicationTemplate.toEntity(): MedicationTemplateEntity = MedicationTemplateEntity(
    id = id,
    brandName = brandName,
    activeIngredient = activeIngredient,
    dosePerTablet = dosePerTablet,
    tabletsPerBox = tabletsPerBox,
)
