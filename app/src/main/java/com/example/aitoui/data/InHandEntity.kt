package com.example.aitoui.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tablets currently "in hand": [quantity] tablets of [medicationId] that have been dispensed into the
 * user's possession. [medicationId] is a foreign key to [Medication.id] (the `medications` table).
 */
@Entity(
    tableName = "in_hand",
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
data class InHandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val quantity: Double,
)

/** Domain model for an in-hand line to be persisted. */
data class InHandItem(
    val medicationId: Long,
    val quantity: Double,
)

fun InHandItem.toEntity(): InHandEntity =
    InHandEntity(medicationId = medicationId, quantity = quantity)
