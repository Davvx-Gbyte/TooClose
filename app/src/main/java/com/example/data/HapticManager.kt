package com.example.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.FaceDistanceStatus

class HapticManager(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private var currentRatio: Float = 0f

    fun updateVibrationForDistance(status: FaceDistanceStatus, ratio: Float, thresholdRatio: Float, enabled: Boolean) {
        val vib = vibrator ?: return
        if (!enabled || !vib.hasVibrator()) {
            vib.cancel()
            return
        }

        if (status == FaceDistanceStatus.TOO_CLOSE) {
            currentRatio = ratio
            // The closer the face (higher ratio), the faster the vibration rate.
            // Ratio goes from thresholdRatio (e.g., 0.42) up to ~0.80.
            val overflow = (ratio - thresholdRatio).coerceAtLeast(0.01f)
            val urgencyFactor = (overflow / 0.35f).coerceIn(0.1f, 1.0f) // 1.0 = extremely close

            // Urgency factor 1.0 -> 80ms delay (rapid), 0.1 -> 600ms delay (slow)
            val pauseMs = (700 - (urgencyFactor * 600)).toLong().coerceAtLeast(80L)
            val pulseMs = (120 + (urgencyFactor * 80)).toLong()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = (150 + (urgencyFactor * 105)).toInt().coerceIn(1, 255)
                val pattern = longArrayOf(0, pulseMs, pauseMs, pulseMs)
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, pulseMs, pauseMs, pulseMs)
                vib.vibrate(pattern, -1)
            }
        } else {
            vib.cancel()
        }
    }

    fun triggerAlertClick() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(100)
        }
    }

    fun stopAll() {
        vibrator?.cancel()
    }
}
