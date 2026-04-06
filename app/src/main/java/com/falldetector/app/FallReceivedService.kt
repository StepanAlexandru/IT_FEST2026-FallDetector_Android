package com.falldetector.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat

class FallReceivedService : Service() {

    private var alarmPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "fall_received_channel"
        const val ALERT_CHANNEL_ID = "fall_received_alert_channel"
        const val NOTIF_ID = 500
        const val ALERT_NOTIF_ID = 501
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val foregroundNotif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fall Alert")
            .setContentText("Procesare alertă...")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, foregroundNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, foregroundNotif)
        }

        acquireWakeLock()

        val name = intent?.getStringExtra("name") ?: "Utilizator"
        val lat = intent?.getStringExtra("lat") ?: "0"
        val lng = intent?.getStringExtra("lng") ?: "0"

        startAlarm()
        showFullScreenAlert(name, lat, lng)
        //launchActivityViaAlarm(name, lat, lng)

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "falldetector:received_alert"
        )
        wakeLock?.acquire(60_000L)
    }

    private fun launchActivityViaAlarm(name: String, lat: String, lng: String) {
        val alarmIntent = Intent(this, AlertReceiver::class.java).apply {
            putExtra("name", name)
            putExtra("lat", lat)
            putExtra("lng", lng)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            System.currentTimeMillis().toInt(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + 100,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + 100,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 100,
                    pendingIntent
                )
            }
            Log.d("FallReceivedService", "Alarm scheduled to launch activity")
        } catch (e: Exception) {
            Log.e("FallReceivedService", "Alarm scheduling failed: ${e.message}")
            try {
                val directIntent = Intent(this, FallReceivedActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("name", name)
                    putExtra("lat", lat)
                    putExtra("lng", lng)
                }
                startActivity(directIntent)
            } catch (e2: Exception) {
                Log.e("FallReceivedService", "Direct launch also failed: ${e2.message}")
            }
        }
    }

    private fun startAlarm() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )

            alarmPlayer = MediaPlayer.create(this, R.raw.alert_notification).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                start()
            }
        } catch (e: Exception) {
            Log.e("FallReceivedService", "Alarm sound failed: ${e.message}")
        }
    }
    fun stopAlarm() {
        alarmPlayer?.stop()
        alarmPlayer?.release()
        alarmPlayer = null
    }

    private fun showFullScreenAlert(name: String, lat: String, lng: String) {
        val fullScreenIntent = Intent(this, FallReceivedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            putExtra("name", name)
            putExtra("lat", lat)
            putExtra("lng", lng)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val notifManager = getSystemService(NotificationManager::class.java)

        notifManager.deleteNotificationChannel(ALERT_CHANNEL_ID)
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID, "Fall Received Alert", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        notifManager.createNotificationChannel(alertChannel)

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("⚠️ $name a căzut!")
            .setContentText("Apasă pentru a vedea locația!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .build()

        notifManager.notify(ALERT_NOTIF_ID, notification)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Fall Received", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}