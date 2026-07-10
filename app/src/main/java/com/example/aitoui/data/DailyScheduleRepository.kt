package com.example.aitoui.data

import kotlinx.coroutines.flow.Flow

class DailyScheduleRepository(private val dao: DailyScheduleDao) {

    /** The current daily schedule (reactive), joined with medication brand names. */
    val dailySchedule: Flow<List<DailyScheduleDetails>> = dao.getAllWithMedicationFlow()

    /** The current daily schedule, joined with medication brand names. */
    suspend fun getAll(): List<DailyScheduleDetails> = dao.getAllWithMedication()

    /** Replaces the whole schedule with [items]. */
    suspend fun save(items: List<DailyScheduleItem>) = dao.replaceAll(items.map { it.toEntity() })
}
