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
    val dateOfIssue: Long,
    val repeats: Int,
    val validToMillis: Long,
)

fun ScriptEntity.toDomain(): Script = Script(
    id = id,
    dispensableUnitId = dispensableUnitId,
    serialNo = serialNo,
    dateOfIssue = dateOfIssue,
    repeats = repeats,
    validToMillis = validToMillis,
)

fun Script.toEntity(): ScriptEntity = ScriptEntity(
    id = id,
    dispensableUnitId = dispensableUnitId,
    serialNo = serialNo,
    dateOfIssue = dateOfIssue,
    repeats = repeats,
    validToMillis = validToMillis,
)
