package com.example.aitoui.data

/**
 * A prescription "Script" — what a doctor writes and you take to the pharmacy.
 * [dispensableUnitId] is a foreign key to the dispensable unit (`dispensable_units` table) it is for.
 */
data class Script(
    val id: Long = 0,
    val dispensableUnitId: Long,
    /** A prescription serial number as printed on the script (e.g. the PBS number). */
    val serialNo: String,
    /** A second serial number (e.g. the eRx token). Interchangeable with [serialNo]; either must be unique. */
    val serialNo2: String = "",
    /** Date the script was issued, as UTC epoch millis at the start of the day. */
    val dateOfIssue: Long,
    val repeats: Int,
    val validToMillis: Long,
    /** Directions for use, e.g. "Take ONE tablet TWICE a day as directed". */
    val instructions: String = "",
)
