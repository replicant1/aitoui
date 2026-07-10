package com.example.aitoui.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dispensations",
    foreignKeys = [
        ForeignKey(
            entity = ScriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["scriptId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DispensableUnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["dispensableUnitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scriptId"), Index("dispensableUnitId")],
)
data class DispensationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scriptId: Long,
    val dispensableUnitId: Long,
    val number: Int,
    val dispensedAtMillis: Long,
)

fun DispensationEntity.toDomain(): Dispensation = Dispensation(
    id = id,
    scriptId = scriptId,
    dispensableUnitId = dispensableUnitId,
    number = number,
    dispensedAtMillis = dispensedAtMillis,
)

fun Dispensation.toEntity(): DispensationEntity = DispensationEntity(
    id = id,
    scriptId = scriptId,
    dispensableUnitId = dispensableUnitId,
    number = number,
    dispensedAtMillis = dispensedAtMillis,
)
