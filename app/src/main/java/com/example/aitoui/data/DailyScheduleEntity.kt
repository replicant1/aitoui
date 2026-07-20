package com.example.aitoui.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One line of the daily medication schedule: [quantity] tablets of [dispensableUnitId] taken each day.
 * [dispensableUnitId] is a foreign key to [DispensableUnit.id] (the `dispensable_units` table), so the
 * schedule is kept per dose/format rather than per medication.
 */
@Entity(
    tableName = "daily_schedule",
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
data class DailyScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dispensableUnitId: Long,
    val quantity: Double,
)

/** Domain model for a daily-schedule line to be persisted. */
data class DailyScheduleItem(
    val dispensableUnitId: Long,
    val quantity: Double,
)

fun DailyScheduleItem.toEntity(): DailyScheduleEntity =
    DailyScheduleEntity(dispensableUnitId = dispensableUnitId, quantity = quantity)
