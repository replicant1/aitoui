package com.example.aitoui.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dispensable_units",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicationId")],
)
data class MedicationFormatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val dosePerTablet: String,
    val tabletsPerUnit: String,
)

fun MedicationFormatEntity.toDomain(): MedicationFormat = MedicationFormat(
    id = id,
    medicationId = medicationId,
    dosePerTablet = dosePerTablet,
    tabletsPerUnit = tabletsPerUnit,
)

fun MedicationFormat.toEntity(): MedicationFormatEntity = MedicationFormatEntity(
    id = id,
    medicationId = medicationId,
    dosePerTablet = dosePerTablet,
    tabletsPerUnit = tabletsPerUnit,
)
