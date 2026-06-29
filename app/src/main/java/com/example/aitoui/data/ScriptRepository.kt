package com.example.aitoui.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScriptRepository(private val dao: ScriptDao) {

    val scripts: Flow<List<Script>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    /** Scripts joined with medication/format details, for the Dispense script dropdown. */
    val scriptsWithDetails: Flow<List<ScriptDetails>> = dao.getAllWithDetails()

    suspend fun add(script: Script): Long = dao.insert(script.toEntity())

    suspend fun update(script: Script) = dao.update(script.toEntity())

    suspend fun delete(script: Script) = dao.delete(script.toEntity())

    suspend fun getById(id: Long): Script? = dao.getById(id)?.toDomain()
}
