package com.example.aitoui.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScriptRepository(private val dao: ScriptDao) {

    val scripts: Flow<List<Script>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    /** Scripts joined with medication/format details, for the Scripts list screen. */
    val scriptsWithDetails: Flow<List<ScriptDetails>> = dao.getAllWithDetails()

    /**
     * True if any of [serials] already appears as a serial number on an existing script (in either serial
     * slot). [serials] should already be trimmed and non-blank; an empty list is never a duplicate.
     */
    suspend fun anySerialInUse(serials: List<String>): Boolean =
        serials.isNotEmpty() && dao.countMatchingSerials(serials) > 0

    suspend fun add(script: Script): Long = dao.insert(script.toEntity())

    suspend fun update(script: Script) = dao.update(script.toEntity())

    suspend fun delete(script: Script) = dao.delete(script.toEntity())

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun getById(id: Long): Script? = dao.getById(id)?.toDomain()
}
