package com.example.aitoui.data

import android.content.Context
import android.content.SharedPreferences
import com.example.aitoui.alerts.DEFAULT_WARNING_DAYS
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * User preferences backed by [SharedPreferences]. These live deliberately OUTSIDE the Room database, so they
 * are excluded from the Save/Load backup (which only captures the database and images — see BackupManager).
 *
 * The single preference today is the "warning window": how many days ahead the main screen's attention
 * messages start warning about low supply, scripts to refill, and medications to restock.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Emits the warning-window size (days) immediately, then again whenever it changes. */
    val warningWindowDays: Flow<Int> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_WARNING_DAYS || key == null) trySend(readWarningWindowDays())
        }
        trySend(readWarningWindowDays())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    /** The current warning-window size in days (defaults to [DEFAULT_WARNING_DAYS] when never set). */
    fun currentWarningWindowDays(): Int = readWarningWindowDays()

    /** Stores a new warning-window size in days. */
    fun setWarningWindowDays(days: Int) {
        prefs.edit().putInt(KEY_WARNING_DAYS, days).apply()
    }

    private fun readWarningWindowDays(): Int = prefs.getInt(KEY_WARNING_DAYS, DEFAULT_WARNING_DAYS)

    companion object {
        private const val PREFS_NAME = "pxtx_settings"
        private const val KEY_WARNING_DAYS = "warning_window_days"
    }
}
