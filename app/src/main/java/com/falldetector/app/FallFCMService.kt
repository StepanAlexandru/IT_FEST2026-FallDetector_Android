package com.falldetector.app

import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FallFCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .update("fcmToken", token)
        Log.d("FCM", "Token updated: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM", "Message received! Data: ${message.data}")

        val fromName = message.data["fromName"] ?: "Utilizator"
        val lat = message.data["latitude"] ?: "0"
        val lng = message.data["longitude"] ?: "0"

        // 1. Wake screen IMMEDIATELY
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "falldetector:fcm_wake"
        )
        wl.acquire(15_000L)

        // 2. Launch activity DIRECTLY — fastest possible
        try {
            val activityIntent = Intent(this, FallReceivedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra("name", fromName)
                putExtra("lat", lat)
                putExtra("lng", lng)
            }
            startActivity(activityIntent)
            Log.d("FCM", "Activity launched directly from FCM")
        } catch (e: Exception) {
            Log.e("FCM", "Direct launch failed: ${e.message}")
        }

        // 3. Start service for alarm sound + notification backup
        val serviceIntent = Intent(this, FallReceivedService::class.java).apply {
            putExtra("name", fromName)
            putExtra("lat", lat)
            putExtra("lng", lng)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Release wake lock after a short delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (wl.isHeld) wl.release()
        }, 5000)
    }
}