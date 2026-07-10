package com.example.aitoui.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One line of the daily medication schedule: [quantity] tablets of [medicationId] taken each day.
 * [medicationId] is a foreign key to [Medication.id] (the `medications` table).
 */
@Entity(
    tableName = "daily_schedule",
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
data class DailyScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val quantity: Double,
)

/** Domain model for a daily-schedule line to be persisted. */
data class DailyScheduleItem(
    val medicationId: Long,
    val quantity: Double,
)

fun DailyScheduleItem.toEntity(): DailyScheduleEntity =
    DailyScheduleEntity(medicationId = medicationId, quantity = quantity)
