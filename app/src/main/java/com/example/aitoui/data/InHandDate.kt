package com.example.aitoui.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.floor

private const val DAY_MILLIS = 86_400_000.0

/**
 * The date the In Hand figures were gathered — i.e. when the user last pressed "Save" on the In Hand
 * screen, as UTC epoch millis at the start of that day. There is at most one row (fixed [id]); each
 * save overwrites it, so this table never grows.
 */
@Entity(tableName = "in_hand_date")
data class InHandDateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val gatheredAtMillis: Long,
) {
    companion object {
        /** The one and only primary key for this single-row table. */
        const val SINGLETON_ID = 0
    }
}

/**
 * Whole days elapsed between when the in-hand figures were gathered ([gatheredAtMillis]) and [nowMillis].
 * Returns 0 when no in-hand data has ever been saved (null) or the clock sits before the gathered date,
 * so supply calculations fall back to treating the figures as current.
 */
fun inHandDaysElapsed(gatheredAtMillis: Long?, nowMillis: Long): Double =
    if (gatheredAtMillis == null) 0.0
    else floor((nowMillis - gatheredAtMillis) / DAY_MILLIS).coerceAtLeast(0.0)
