package io.github.potsdam_pnp.initiative_tracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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

        val app = application as InitiativeTrackerApplication

        clientConnections = ClientConnections(app.snapshot, app.connectionManager)
        server = Server("Unnamed", app.snapshot, app.connectionManager)
        server!!.toggle(true)
        serverJob = lifecycleScope.launch(Dispatchers.Default) {
            server!!.runOnce()
        }
        clientConnectionJob = lifecycleScope.launch(Dispatchers.Default) {
            clientConnections!!.run(lifecycleScope)
        }
        connectionManagerJob = lifecycleScope.launch(Dispatchers.Default) {
            app.connectionManager.run()
        }
        lifecycleScope.launch {
            delay(1000)
            server!!.toggle(true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("stop", false) == true) {
            Napier.i("stop command")
            if (!isShuttingDown) {
                isShuttingDown = true
                server?.toggle(false)

                lifecycleScope.launch {
                    serverJob?.join()
                    clientConnectionJob?.cancelAndJoin()
                    connectionManagerJob?.cancelAndJoin()

                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } else {
            Napier.i("start command")
            server?.toggle(true)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private var isShuttingDown = false
    private var connectionManagerJob: Job? = null
    private var clientConnectionJob: Job? = null
    private var serverJob: Job? = null

    var server: Server? = null
    var clientConnections: ClientConnections? = null
}