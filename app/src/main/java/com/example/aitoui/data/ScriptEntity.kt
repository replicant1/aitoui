package com.example.aitoui.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scripts",
    foreignKeys = [
        ForeignKey(
            entity = MedicationFormatEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationFormatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicationFormatId")],
)
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationFormatId: Long,
    val directions: String,
    val quantity: Int,
    val repeats: Int,
    val validToMillis: Long,
    val dispensed: Int = 0,
)

fun ScriptEntity.toDomain(): Script = Script(
    id = id,
    medicationFormatId = medicationFormatId,
    directions = directions,
    quantity = quantity,
    repeats = repeats,
    validToMillis = validToMillis,
    dispensed = dispensed,
)

fun Script.toEntity(): ScriptEntity = ScriptEntity(
    id = id,
    medicationFormatId = medicationFormatId,
    directions = directions,
    quantity = quantity,
    repeats = repeats,
    validToMillis = validToMillis,
    dispensed = dispensed,
)
