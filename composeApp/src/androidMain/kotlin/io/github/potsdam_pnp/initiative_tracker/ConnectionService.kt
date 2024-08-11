package io.github.potsdam_pnp.initiative_tracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch

class ConnectionService: LifecycleService() {
    private fun channelId(): String {
        val channel = NotificationChannel(
            "BACKGROUND_SERVICE_1",
            "background service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background service for initiative tracker"
        }

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).apply {
            createNotificationChannel(channel)
        }

        return "BACKGROUND_SERVICE_1"
    }

    override fun onCreate() {
        super.onCreate()

        Napier.i("Service created (1)")

        val notification = NotificationCompat.Builder(this, channelId()).apply {
            setSmallIcon(R.drawable.ic_launcher_foreground)
            setContentTitle("Server Running")
            setContentText("Connected to 0 other apps, awaiting other connections")
            setPriority(NotificationCompat.PRIORITY_LOW)
            setOngoing(true)
            setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            val actionIntent =
                Intent(this@ConnectionService, ConnectionService::class.java).apply {
                    putExtra("stop", true)
                }
            addAction(
                R.drawable.ic_launcher_foreground,
                "Stop",
                PendingIntent.getService(
                    this@ConnectionService, 1, actionIntent, PendingIntent.FLAG_IMMUTABLE)
            )
        }.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(100, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground( 100, notification)
        }

        Napier.i("Service created")

        server = Server(this, (application as InitiativeTrackerApplication).model)
        lifecycleScope.launch {
            server!!.run(lifecycleScope)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent!!.getBooleanExtra("stop", false)) {
            Napier.i("stop command")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            Napier.i("start command")
        }
        return super.onStartCommand(intent, flags, startId)
    }

    var server: Server? = null

}