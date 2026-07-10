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
data class DispensableUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val dosePerTablet: String,
    val tabletsPerUnit: String,
)

fun DispensableUnitEntity.toDomain(): DispensableUnit = DispensableUnit(
    id = id,
    medicationId = medicationId,
    dosePerTablet = dosePerTablet,
    tabletsPerUnit = tabletsPerUnit,
)

fun DispensableUnit.toEntity(): DispensableUnitEntity = DispensableUnitEntity(
    id = id,
    medicationId = medicationId,
    dosePerTablet = dosePerTablet,
    tabletsPerUnit = tabletsPerUnit,
)
