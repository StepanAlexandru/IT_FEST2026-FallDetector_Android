package com.falldetector.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.*
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.sqrt
import android.os.PowerManager

class FallDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLat = 0.0
    private var lastLng = 0.0

    private var inFreefall = false
    private var fallCandidateTime = 0L
    private val FALL_THRESHOLD = 2.5f
    private val IMPACT_THRESHOLD = 20.0f
    private val FALL_WINDOW_MS = 1500L
    private var lastAlertTime = 0L
    private val ALERT_COOLDOWN_MS = 10_000L
    private var alarmPlayer: MediaPlayer? = null
    private var countdownTimer: android.os.CountDownTimer? = null
    private var alertAlreadySent = false

    companion object {
        const val CHANNEL_ID = "fall_detection_channel"
        const val NOTIF_ID = 1
        var fallDetectedAt = 0L
            private set

        fun startService(context: Context) {
            val intent = Intent(context, FallDetectionService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, FallDetectionService::class.java))
        }

        fun triggerFallAlert(context: Context) {
            val intent = Intent(context, FallDetectionService::class.java)
            intent.action = "SEND_FALL_ALERT"
            context.startService(intent)
        }

        fun stopAlarmStatic(context: Context) {
            val intent = Intent(context, FallDetectionService::class.java)
            intent.action = "STOP_ALARM"
            context.startService(intent)
        }

        fun cancelAlert(context: Context) {
            val intent = Intent(context, FallDetectionService::class.java)
            intent.action = "CANCEL_ALERT"
            context.startService(intent)
        }

        fun triggerVoiceAlert(context: Context) {
            val intent = Intent(context, FallDetectionService::class.java)
            intent.action = "VOICE_ALERT"
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startLocationUpdates()
        Log.d("FallService", "Service started!")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SEND_FALL_ALERT" -> {
                countdownTimer?.cancel()
                sendFallToFirestore()
                stopAlarm()
            }
            "STOP_ALARM" -> {
                // Only stop the alarm sound, DON'T cancel countdown
                stopAlarm()
            }
            "CANCEL_ALERT" -> {
                countdownTimer?.cancel()
                alertAlreadySent = true
                stopAlarm()
            }
            "VOICE_ALERT" -> {
                onFallDetected()
            }
        }
        return START_STICKY
    }
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L
        ).build()
        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.let {
                            lastLat = it.latitude
                            lastLng = it.longitude
                            Log.d("FallService", "Location updated: $lastLat, $lastLng")
                            val uid = auth.currentUser?.uid ?: return
                            db.collection("users").document(uid)
                                .update("lastLatitude", lastLat, "lastLongitude", lastLng)
                        }
                    }
                },
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("FallService", "Location permission missing: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            if (magnitude < FALL_THRESHOLD && !inFreefall) {
                inFreefall = true
                fallCandidateTime = System.currentTimeMillis()
            }

            if (inFreefall && magnitude > IMPACT_THRESHOLD) {
                val timeSinceFall = System.currentTimeMillis() - fallCandidateTime
                if (timeSinceFall < FALL_WINDOW_MS) {
                    Log.w("FallDetect", "FALL DETECTED!")
                    onFallDetected()
                }
                inFreefall = false
            }

            if (inFreefall && System.currentTimeMillis() - fallCandidateTime > FALL_WINDOW_MS * 2) {
                inFreefall = false
            }
        }
    }

    private fun onFallDetected() {
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < ALERT_COOLDOWN_MS) return
        lastAlertTime = now
        fallDetectedAt = now
        alertAlreadySent = false
        startAlarmFromService()

        // Launch activity DIRECTLY — instant
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "falldetector:fall_wake"
            )
            wl.acquire(20_000L)

            val activityIntent = Intent(this, FallAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(activityIntent)
            Log.d("FallService", "FallAlertActivity launched directly")
        } catch (e: Exception) {
            Log.e("FallService", "Direct launch failed: ${e.message}")
        }

        showFullScreenAlert()       // notification as backup
        //launchAlertViaAlarm()       // alarm as second backup
        startCountdownInService()
    }

    private fun launchAlertViaAlarm() {
        val alarmIntent = Intent(this, FallAlertReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            System.currentTimeMillis().toInt(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() + 100,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() + 100,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + 100,
                    pendingIntent
                )
            }
            Log.d("FallService", "Alarm scheduled to launch FallAlertActivity")
        } catch (e: Exception) {
            Log.e("FallService", "Alarm failed: ${e.message}")
        }
    }

    private fun startCountdownInService() {
        countdownTimer?.cancel()
        countdownTimer = object : android.os.CountDownTimer(15000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                Log.d("FallService", "Countdown: ${millisUntilFinished / 1000}s")
            }
            override fun onFinish() {
                Log.w("FallService", "Countdown finished - sending alert automatically!")
                sendFallToFirestore()
                callEmergencyNumber()
                stopAlarm()
            }
        }.start()
    }

    private fun callEmergencyNumber() {
        try {
            // Pentru teste: număr inexistent
            // Pentru producție: înlocuiește cu "112"
            val emergencyNumber = "0723572574"

            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = android.net.Uri.parse("tel:$emergencyNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(callIntent)
            Log.d("FallService", "Emergency call initiated to $emergencyNumber")
        } catch (e: SecurityException) {
            Log.e("FallService", "Call permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e("FallService", "Call failed: ${e.message}")
        }
    }


    private fun startAlarmFromService() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )

            alarmPlayer = MediaPlayer.create(this, R.raw.alert_sound).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                start()
            }
            Log.d("FallService", "Alarm started!")
        } catch (e: Exception) {
            Log.e("FallService", "Alarm failed: ${e.message}")
        }
    }

    fun stopAlarm() {
        alarmPlayer?.stop()
        alarmPlayer?.release()
        alarmPlayer = null
        Log.d("FallService", "Alarm stopped!")
    }

    private fun showFullScreenAlert() {
        val fullScreenIntent = Intent(this, FallAlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alertChannelId = "fall_alert_fullscreen"
        val notifManager = getSystemService(NotificationManager::class.java)

        val alertChannel = NotificationChannel(
            alertChannelId, "Fall Alert", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notifManager.createNotificationChannel(alertChannel)

        val notification = NotificationCompat.Builder(this, alertChannelId)
            .setContentTitle("⚠️ CĂDERE DETECTATĂ!")
            .setContentText("Ești bine? Apasă pentru a răspunde!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        notifManager.notify(FallAlertActivity.NOTIFICATION_ID, notification)
    }

    fun sendFallToFirestore() {
        if (alertAlreadySent) return
        alertAlreadySent = true

        val uid = auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()

        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val name = userDoc.getString("name") ?: "Utilizator"
            val phone = userDoc.getString("phone") ?: ""

            val fallEvent = hashMapOf(
                "uid" to uid,
                "name" to name,
                "phone" to phone,
                "timestamp" to now,
                "latitude" to lastLat,
                "longitude" to lastLng,
                "status" to "confirmed"
            )

            db.collection("fall_events").add(fallEvent)
                .addOnSuccessListener { docRef ->
                    Log.d("FallDetect", "Fall saved: ${docRef.id}")
                    notifyAllUsers(uid, name, docRef.id)
                }
        }
    }

    private fun notifyAllUsers(fromUid: String, fromName: String, eventId: String) {
        db.collection("users").get().addOnSuccessListener { users ->
            users.forEach { userDoc ->
                val targetUid = userDoc.getString("uid") ?: return@forEach
                if (targetUid == fromUid) return@forEach

                val notification = hashMapOf(
                    "toUid" to targetUid,
                    "fromUid" to fromUid,
                    "fromName" to fromName,
                    "eventId" to eventId,
                    "timestamp" to System.currentTimeMillis(),
                    "read" to false
                )
                db.collection("notifications").add(notification)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
        stopAlarm()
        sensorManager.unregisterListener(this)
        Log.d("FallService", "Service stopped!")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Fall Detection", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitorizare cădere activă"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fall Detector activ")
            .setContentText("Monitorizare în fundal...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}