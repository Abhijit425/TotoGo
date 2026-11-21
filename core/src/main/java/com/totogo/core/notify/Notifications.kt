package com.totogo.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.totogo.core.R

object TotoNotifications {
    /** Bumped name so the custom sound applies even if an older channel exists (channels are immutable). */
    const val CHANNEL_RIDE_REQUEST = "ride_requests_v1"
    const val CHANNEL_RIDE_UPDATES = "ride_updates"
    const val CHANNEL_TRACKING = "location_tracking"
    const val ID_RIDE_REQUEST = 4001
    const val ID_RIDE_UPDATE = 4002
    const val ID_TRACKING = 4003

    fun alarmUri(context: Context): Uri =
        Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.totogo_alarm}")

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RIDE_REQUEST, "Incoming ride requests", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Loud TotoGo alarm when a passenger near you requests a Toto"
                setSound(alarmUri(context), alarmAttrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 250, 600, 250, 900)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(false)
            }
        )
        nm.createNotificationChannel(NotificationChannel(CHANNEL_RIDE_UPDATES, "Ride updates", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Driver accepted, arrived, ride completed"
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TRACKING, "Live location sharing", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while your location is shared during online time or an active ride"
        })
    }

    /** High-priority heads-up + full-screen intent so the request screen appears over the lock screen. */
    fun showRideRequest(context: Context, title: String, body: String, launch: Intent) {
        val pi = PendingIntent.getActivity(context, 7, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_RIDE_REQUEST)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(alarmUri(context))
            .setColor(0xFFFFB300.toInt())
            .setAutoCancel(true)
            .setTimeoutAfter(25_000)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_RIDE_REQUEST, n) }
    }

    fun cancelRideRequest(context: Context) = NotificationManagerCompat.from(context).cancel(ID_RIDE_REQUEST)

    fun showUpdate(context: Context, title: String, body: String, launch: Intent) {
        val pi = PendingIntent.getActivity(context, 8, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_RIDE_UPDATES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setContentIntent(pi).build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_RIDE_UPDATE, n) }
    }
}

/** Loud looping in-app alarm + vibration while the incoming request screen is visible. */
class RideAlarm(private val context: Context) {
    private var player: MediaPlayer? = null

    fun start() {
        if (player != null) return
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setDataSource(context, TotoNotifications.alarmUri(context))
            isLooping = true
            setOnPreparedListener { it.start() }
            prepareAsync()
        }
        vibrator()?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 300, 700, 300, 1000), 0))
    }

    fun stop() {
        runCatching { player?.stop() }
        player?.release()
        player = null
        vibrator()?.cancel()
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
}
