package com.example.simplemusic

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

class PlayerService : Service() {

    companion object {
        const val CH = "playback"
        const val ID = 1
        const val ACTION_TOGGLE = "simplemusic.toggle"
        const val ACTION_NEXT = "simplemusic.next"
        const val ACTION_PREV = "simplemusic.prev"
        const val ACTION_STOP = "simplemusic.stop"
    }

    private val onChange: () -> Unit = { refresh() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH, "Playback", NotificationManager.IMPORTANCE_LOW)
        )
        Player.listeners.add(onChange)
        val n = build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(ID, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> Player.toggle()
            ACTION_NEXT -> Player.next()
            ACTION_PREV -> Player.prev()
            ACTION_STOP -> Player.stop()
        }
        return START_NOT_STICKY
    }

    private fun refresh() {
        if (Player.current == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            getSystemService(NotificationManager::class.java).notify(ID, build())
        }
    }

    private fun action(icon: Int, label: String, action: String): Notification.Action {
        val pi = PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, PlayerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build()
    }

    private fun build(): Notification {
        val song = Player.current
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggleIcon = if (Player.isPlaying) android.R.drawable.ic_media_pause
        else android.R.drawable.ic_media_play
        return Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song?.title ?: "Simple Music")
            .setContentText(song?.artist ?: "")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREV))
            .addAction(action(toggleIcon, "Play/Pause", ACTION_TOGGLE))
            .addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_STOP))
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    override fun onDestroy() {
        Player.listeners.remove(onChange)
        super.onDestroy()
    }
}
