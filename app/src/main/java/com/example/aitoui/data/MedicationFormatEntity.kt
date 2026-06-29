package com.example.aitoui.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medication_formats",
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
    val tabletsPerBox: String,
)

fun MedicationFormatEntity.toDomain(): MedicationFormat = MedicationFormat(
    id = id,
    medicationId = medicationId,
    dosePerTablet = dosePerTablet,
    tabletsPerBox = tabletsPerBox,
)

fun MedicationFormat.toEntity(): MedicationFormatEntity = MedicationFormatEntity(
    id = id,
    medicationId = medicationId,
    dosePerTablet = dosePerTablet,
    tabletsPerBox = tabletsPerBox,
)
