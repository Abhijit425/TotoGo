package com.totogo.driver

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.totogo.core.location.FusedLocationProvider
import com.totogo.core.location.hasLocationPermission
import com.totogo.core.notify.TotoNotifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground service that shares GPS only while the driver is ONLINE.
 * Started by the app when the driver goes online, stopped the moment they go offline.
 * Requires location permission to have been granted while the app was in the foreground.
 */
class DriverLocationService : LifecycleService() {
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val app = application as DriverApp
        if (!hasLocationPermission(this)) { stopSelf(); return START_NOT_STICKY }
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, TotoNotifications.ID_TRACKING, notification(app.repo.isDemo), type)
        if (job == null) {
            job = lifecycleScope.launch {
                FusedLocationProvider(this@DriverLocationService).updates(4_000).collect { fix ->
                    if (app.settings.value.useRealGps || !app.repo.isDemo) app.repo.onLocationFix(fix.point)
                }
            }
        }
        return START_STICKY
    }

    private fun notification(demo: Boolean): Notification {
        val pi = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, TotoNotifications.CHANNEL_TRACKING)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("You're online${if (demo) " (DEMO)" else ""}")
            .setContentText("Sharing location with TotoGo so nearby passengers can find you. Go offline to stop.")
            .setOngoing(true).setContentIntent(pi).build()
    }

    override fun onDestroy() { job?.cancel(); super.onDestroy() }

    companion object {
        fun start(context: Context) {
            if (!hasLocationPermission(context)) return // demo still works with simulated GPS
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DriverLocationService::class.java)) }
        }
        fun stop(context: Context) { context.stopService(Intent(context, DriverLocationService::class.java)) }
    }
}
