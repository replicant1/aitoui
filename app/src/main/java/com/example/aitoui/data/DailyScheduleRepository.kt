package com.example.aitoui.data

class DailyScheduleRepository(private val dao: DailyScheduleDao) {

    /** The current daily schedule, joined with medication brand names. */
    suspend fun getAll(): List<DailyScheduleDetails> = dao.getAllWithMedication()

    /** Replaces the whole schedule with [items]. */
    suspend fun save(items: List<DailyScheduleItem>) = dao.replaceAll(items.map { it.toEntity() })
}
