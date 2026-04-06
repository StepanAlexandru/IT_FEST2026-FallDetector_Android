package com.falldetector.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlertReceiver", "Alarm fired — launching FallReceivedActivity")

        // Wake the screen
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "falldetector:alert_wake"
        )
        wl.acquire(10_000L)

        val name = intent.getStringExtra("name") ?: "Utilizator"
        val lat = intent.getStringExtra("lat") ?: "0"
        val lng = intent.getStringExtra("lng") ?: "0"

        val activityIntent = Intent(context, FallReceivedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("name", name)
            putExtra("lat", lat)
            putExtra("lng", lng)
        }
        context.startActivity(activityIntent)

        wl.release()
    }
}