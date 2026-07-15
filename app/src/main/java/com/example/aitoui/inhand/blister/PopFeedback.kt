package com.example.aitoui.inhand.blister

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.aitoui.R

/**
 * Plays the "pop" when a pocket is emptied: a short sound (only when the ringer isn't silenced) paired with a
 * haptic tick (which carries the feedback when the phone is muted). Un-popping is a lighter haptic only.
 * Create once per screen and [release] on dispose.
 */
class PopFeedback(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator: Vibrator? = resolveVibrator(appContext)

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val popSoundId = soundPool.load(appContext, R.raw.pop, 1)

    /** Full → empty: pop sound (if audible) + a haptic tick. */
    fun pop() {
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            soundPool.play(popSoundId, 1f, 1f, 1, 0, 1f)
        }
        vibrate(tick = true)
    }

    /** Empty → full (undo): a lighter haptic only, so undo doesn't compete with the real pop. */
    fun unpop() {
        vibrate(tick = false)
    }

    fun release() {
        soundPool.release()
    }

    private fun vibrate(tick: Boolean) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            vibrateInternal(v, tick)
        } catch (_: Exception) {
            // Never let missing haptics (e.g. permission/OEM quirks) break the pop.
        }
    }

    private fun vibrateInternal(v: Vibrator, tick: Boolean) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val effect = if (tick) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK
                @Suppress("DEPRECATION")
                try { v.vibrate(VibrationEffect.createPredefined(effect)) } catch (_: Exception) {
                    v.vibrate(VibrationEffect.createOneShot(if (tick) 12L else 8L, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                v.vibrate(VibrationEffect.createOneShot(if (tick) 12L else 8L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            else -> {
                @Suppress("DEPRECATION")
                v.vibrate(if (tick) 12L else 8L)
            }
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}
