package com.gogitit.rewinder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import com.gogitit.rewinder.core.RewindPolicy
import com.gogitit.rewinder.core.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Microphone foreground service. Its ongoing notification is the Android version of
 * the "Rewinder in progress" lock screen widget: status, unlock progress, Rewind and Stop.
 *
 * Note: since Android 14 users can swipe away foreground service notifications.
 * Recording continues; the notification comes back on the next update.
 */
class RewinderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private val notifications by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rewinder session",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows while Rewinder is listening."
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notifications.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = rewinderGraph.controller
        when (intent?.action) {
            ACTION_STOP -> controller.stop()
            ACTION_REWIND -> controller.rewind()
            else -> {
                val first = build(NotificationModel(true, 0L, canRewind = false, summarizing = false))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(NOTIFICATION_ID, first)
                }
                observe()
            }
        }
        // If the process dies, the in-memory buffer is gone anyway. Do not restart.
        return START_NOT_STICKY
    }

    private fun observe() {
        if (observeJob != null) return
        observeJob = scope.launch {
            rewinderGraph.controller.state
                .map {
                    NotificationModel(
                        running = it.isRunning,
                        bufferedSeconds = it.bufferedMillis / 1000L,
                        canRewind = it.canRewind,
                        summarizing = it.isSummarizing,
                    )
                }
                .distinctUntilChanged()
                .collect { model ->
                    if (!model.running) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        notifications.notify(NOTIFICATION_ID, build(model))
                    }
                }
        }
    }

    private fun build(model: NotificationModel): Notification {
        val bufferedMillis = model.bufferedSeconds * 1000L
        val text = when {
            model.summarizing -> "Summarizing..."
            model.canRewind -> "${formatDuration(bufferedMillis)} in memory. Rewind is ready."
            else -> "Rewind unlocks in ${formatDuration(RewindPolicy.MIN_REWIND_MILLIS - bufferedMillis)}"
        }
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_stat_rewinder)

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rewinder)
            .setContentTitle("Rewinder in progress")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(0xFFD7372B.toInt())

        if (!model.canRewind && !model.summarizing) {
            builder.setProgress(RewindPolicy.MIN_REWIND_SECONDS, model.bufferedSeconds.toInt(), false)
        }
        if (model.canRewind) {
            builder.addAction(Notification.Action.Builder(icon, "Rewind", actionIntent(ACTION_REWIND, 1)).build())
        }
        builder.addAction(Notification.Action.Builder(icon, "Stop", actionIntent(ACTION_STOP, 2)).build())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode,
        Intent(this, RewinderService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private data class NotificationModel(
        val running: Boolean,
        val bufferedSeconds: Long,
        val canRewind: Boolean,
        val summarizing: Boolean,
    )

    private companion object {
        const val CHANNEL_ID = "rewinder_session"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.gogitit.rewinder.action.STOP"
        const val ACTION_REWIND = "com.gogitit.rewinder.action.REWIND"
    }
}
