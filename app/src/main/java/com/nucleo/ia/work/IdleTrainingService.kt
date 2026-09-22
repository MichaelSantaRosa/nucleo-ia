package com.nucleo.ia.work

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.nucleo.ia.NucleoApp
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class IdleTrainingService : Service() {
    companion object {
        const val NOTIF_CHANNEL = "train_ch"
        const val NOTIF_ID = 1
        fun scheduleNext(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getService(ctx, 1, Intent(ctx, IdleTrainingService::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val next = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(6)
            am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(NOTIF_CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(NOTIF_CHANNEL, "Treino em ociosidade", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        ensureChannel()
        if (Build.VERSION.SDK_INT >= 26) {
            startForeground(NOTIF_ID, Notification.Builder(this, NOTIF_CHANNEL)
                .setContentTitle("Nucleo IA treinando")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build())
        }
        val app = applicationContext as NucleoApp
        CoroutineScope(Dispatchers.Default).launch {
            try { app.engine.trainOnIdle(50, 0.01f) } catch (_: Exception) { }
        }
        scheduleNext(this)
        stopForeground(true); stopSelf()
        return START_NOT_STICKY
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) IdleTrainingService.scheduleNext(ctx)
    }
}

