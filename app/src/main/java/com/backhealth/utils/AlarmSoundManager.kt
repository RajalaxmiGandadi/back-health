package com.backhealth.utils

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.backhealth.alarm.AlarmReceiver

object AlarmSoundManager {

    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var autoSilenceHandler: Handler? = null
    private var autoSilenceRunnable: Runnable? = null

    /**
     * Plays alarm audio on the hardware alarm stream and pulses vibration continuously
     * until the user touches the screen or taps an action button.
     * @param timeoutMs Safety timeout to prevent infinite battery drain if left unattended (default 60s).
     */
    @Synchronized
    fun playAlarm(context: Context, timeoutMs: Long = 60000L) {
        // Stop any previous instance first
        stop(context)

        // 1. Play Alarm Audio via MediaPlayer (Highest priority hardware ALARM audio stream)
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val mp = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            // Fallback to RingtoneManager if device restricts direct MediaPlayer on specific alarm URI
            try {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val rt = RingtoneManager.getRingtone(context.applicationContext, alarmUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    rt.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                rt.play()
                ringtone = rt
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }

        // 2. Start Repeating Vibration Pattern (vibrate 800ms, pause 400ms, repeat continuously)
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // repeat index 0 -> loop continuously until cancelled
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Safety auto-silence timer (60s) so audio never blares infinitely if phone is unattended
        autoSilenceHandler = Handler(Looper.getMainLooper())
        autoSilenceRunnable = Runnable {
            stop(context)
        }
        autoSilenceHandler?.postDelayed(autoSilenceRunnable!!, timeoutMs)
    }

    /**
     * Backward-compatible shorthand.
     */
    @Synchronized
    fun play(context: Context) {
        playAlarm(context)
    }

    @Synchronized
    fun stop(context: Context) {
        // Cancel auto-silence timer
        autoSilenceRunnable?.let { autoSilenceHandler?.removeCallbacks(it) }
        autoSilenceRunnable = null
        autoSilenceHandler = null

        // Stop and release MediaPlayer immediately
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null

        // Stop Ringtone fallback
        try {
            ringtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ringtone = null

        // Stop Vibrator
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vibrator = null

        // Dismiss outside alarm notifications
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(AlarmReceiver.NOTIFICATION_ID_EXERCISE)
            nm.cancel(AlarmReceiver.NOTIFICATION_ID_WALK)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
