package com.example.aitoui.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scripts",
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
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dispensableUnitId: Long,
    val serialNo: String,
    val serialNo2: String = "",
    val dateOfIssue: Long,
    val repeats: Int,
    val validToMillis: Long,
    /** Directions for use, e.g. "Take ONE tablet TWICE a day as directed". */
    val instructions: String = "",
)

fun ScriptEntity.toDomain(): Script = Script(
    id = id,
    dispensableUnitId = dispensableUnitId,
    serialNo = serialNo,
    serialNo2 = serialNo2,
    dateOfIssue = dateOfIssue,
    repeats = repeats,
    validToMillis = validToMillis,
    instructions = instructions,
)

fun Script.toEntity(): ScriptEntity = ScriptEntity(
    id = id,
    dispensableUnitId = dispensableUnitId,
    serialNo = serialNo,
    serialNo2 = serialNo2,
    dateOfIssue = dateOfIssue,
    repeats = repeats,
    validToMillis = validToMillis,
    instructions = instructions,
)
