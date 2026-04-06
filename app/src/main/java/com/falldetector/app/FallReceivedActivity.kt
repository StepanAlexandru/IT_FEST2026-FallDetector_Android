package com.falldetector.app

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.firestore.FirebaseFirestore

class FallReceivedActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context, name: String, lat: String, lng: String) {
            val intent = Intent(context, FallReceivedActivity::class.java).apply {
                putExtra("name", name)
                putExtra("lat", lat)
                putExtra("lng", lng)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }

        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.cancel(FallReceivedService.ALERT_NOTIF_ID)

        setContentView(R.layout.activity_fall_received)

        val name = intent.getStringExtra("name") ?: "Utilizator"
        val lat = intent.getStringExtra("lat") ?: "0"
        val lng = intent.getStringExtra("lng") ?: "0"

        findViewById<TextView>(R.id.tvFallName).text = "$name a căzut!"
        findViewById<TextView>(R.id.tvFallLocation).text = "$lat, $lng"

        // Load medical profile
        loadMedicalProfile(name)

        findViewById<AppCompatButton>(R.id.btnOpenMaps).setOnClickListener {
            stopReceivedService()
            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($name)")
            val mapsIntent = Intent(Intent.ACTION_VIEW, uri)
            mapsIntent.setPackage("com.google.android.apps.maps")
            if (mapsIntent.resolveActivity(packageManager) != null) {
                startActivity(mapsIntent)
            } else {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://maps.google.com/?q=$lat,$lng")
                )
                startActivity(browserIntent)
            }
            finish()
        }

        findViewById<AppCompatButton>(R.id.btnOk).setOnClickListener {
            stopReceivedService()
            finish()
        }
    }

    private fun loadMedicalProfile(name: String) {
        val db = FirebaseFirestore.getInstance()

        db.collection("users")
            .whereEqualTo("name", name)
            .limit(1)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) return@addOnSuccessListener

                val doc = docs.first()
                val conditions = doc.getString("medicalConditions") ?: ""
                val allergies = doc.getString("allergies") ?: ""
                val medication = doc.getString("medication") ?: ""

                if (conditions.isEmpty() && allergies.isEmpty() && medication.isEmpty()) return@addOnSuccessListener

                val medicalSection = findViewById<LinearLayout>(R.id.medicalSection)
                medicalSection.visibility = View.VISIBLE

                if (conditions.isNotEmpty()) {
                    findViewById<TextView>(R.id.tvConditions).text = conditions
                    findViewById<LinearLayout>(R.id.conditionsRow).visibility = View.VISIBLE
                }
                if (allergies.isNotEmpty()) {
                    findViewById<TextView>(R.id.tvAllergies).text = allergies
                    findViewById<LinearLayout>(R.id.allergiesRow).visibility = View.VISIBLE
                }
                if (medication.isNotEmpty()) {
                    findViewById<TextView>(R.id.tvMedication).text = medication
                    findViewById<LinearLayout>(R.id.medicationRow).visibility = View.VISIBLE
                }
            }
    }

    private fun stopReceivedService() {
        val intent = Intent(this, FallReceivedService::class.java)
        stopService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopReceivedService()
    }
}