package com.evac.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.evac.app.MainActivity
import com.evac.app.R
import kotlinx.coroutines.*
import kotlin.math.sin

object AcousticBeacon {
    private var job: Job? = null
    var isPlaying = false
        private set

    private const val NOTIFY_ID = 8888
    private const val CHANNEL_ID = "beacon_channel"
    const val ACTION_STOP_BEACON = "com.evac.app.STOP_BEACON"

    fun toggle(context: Context) {
        if (isPlaying) stop(context) else start(context)
    }

    fun start(context: Context, distanceToResponder: Float? = null) {
        if (isPlaying) return
        isPlaying = true
        showNotification(context)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)

        job = CoroutineScope(Dispatchers.IO).launch {
            val startTime = System.currentTimeMillis()
            while (isActive && System.currentTimeMillis() - startTime < 10 * 60 * 1000L) { // 10 min timeout
                // 5 bursts
                for (i in 0 until 5) {
                    if (!isActive) break
                    playTone(800.0, 500)
                    if (!isActive) break
                    playTone(1200.0, 500)
                }
                if (!isActive) break

                val delayMs = calculateDelay(context, distanceToResponder)
                delay(delayMs)
            }
            stop(context)
        }
    }

    fun stop(context: Context) {
        if (!isPlaying) return
        isPlaying = false
        job?.cancel()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFY_ID)
    }

    private fun calculateDelay(context: Context, distance: Float?): Long {
        if (distance != null && distance < 50f) return 10_000L // 10s if close
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level < 20) return 120_000L // 2 minutes if battery low
        return 60_000L // Default 60s
    }

    private fun playTone(freqHz: Double, durationMs: Int) {
        val sampleRate = 44100
        val count = (sampleRate * 2.0 * durationMs / 1000.0).toInt() and 1.inv() // Ensure even count
        val samples = ShortArray(count / 2)
        
        for (i in samples.indices) {
            val sample = (sin(2 * Math.PI * i / (sampleRate / freqHz)) * Short.MAX_VALUE).toInt().toShort()
            samples[i] = sample
        }
        
        val track = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            count,
            AudioTrack.MODE_STATIC
        )
        track.write(samples, 0, samples.size)
        track.play()
        Thread.sleep(durationMs.toLong())
        track.release()
    }

    private fun showNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Acoustic Beacon", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(ch)
        }

        val stopIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_STOP_BEACON
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Using ic_evac_logo assuming it exists. If not, use standard Android warning icon.
        val icon = context.resources.getIdentifier("ic_evac_logo", "drawable", context.packageName).let {
            if (it != 0) it else android.R.drawable.ic_dialog_alert
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("Acoustic Beacon Active")
            .setContentText("Emitting high-frequency locator beep...")
            .setOngoing(true)
            .addAction(0, "STOP BEACON", pi)
            .build()
        nm.notify(NOTIFY_ID, notif)
    }
}
