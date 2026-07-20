package com.example.aitoui.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tablets currently "in hand": [quantity] tablets of [dispensableUnitId] that have been dispensed into the
 * user's possession. [dispensableUnitId] is a foreign key to [DispensableUnit.id] (the `dispensable_units`
 * table), so stock is tracked per dose/format rather than per medication.
 */
@Entity(
    tableName = "in_hand",
    foreignKeys = [
        ForeignKey(
            entity = DispensableUnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["dispensableUnitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dispensableUnitId")],
)
data class InHandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dispensableUnitId: Long,
    val quantity: Double,
)

/** Domain model for an in-hand line to be persisted. */
data class InHandItem(
    val dispensableUnitId: Long,
    val quantity: Double,
)

fun InHandItem.toEntity(): InHandEntity =
    InHandEntity(dispensableUnitId = dispensableUnitId, quantity = quantity)
