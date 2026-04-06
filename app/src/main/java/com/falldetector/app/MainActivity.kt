package com.falldetector.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var isMonitoring = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()
        requestBatteryOptimizationExemption()
        checkFullScreenIntentPermission()
        checkExactAlarmPermission()

        val uid = auth.currentUser?.uid
        if (uid != null) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                db.collection("users").document(uid)
                    .update("fcmToken", token)
            }
        }

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvUserName = findViewById<TextView>(R.id.tvUserName)
        val btnToggle = findViewById<AppCompatButton>(R.id.btnToggle)
        val btnLogout = findViewById<AppCompatButton>(R.id.btnLogout)

        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("name") ?: ""
                    tvUserName.text = "Salut, $name!"
                }
        }

        tvStatus.text = "Monitorizare activă"
        FallDetectionService.startService(this)
        VoiceDetectionService.startService(this)

        btnToggle.setOnClickListener {
            if (isMonitoring) {
                FallDetectionService.stopService(this)
                VoiceDetectionService.stopService(this)
                tvStatus.text = "Monitorizare oprită"
                btnToggle.text = "Pornește monitorizarea"
                isMonitoring = false
            } else {
                FallDetectionService.startService(this)
                VoiceDetectionService.startService(this)
                tvStatus.text = "Monitorizare activă"
                btnToggle.text = "Oprește monitorizarea"
                isMonitoring = true
            }
        }

        findViewById<AppCompatButton>(R.id.btnMedical).setOnClickListener {
            startActivity(Intent(this, MedicalProfileActivity::class.java))
        }

        findViewById<AppCompatButton>(R.id.btnEmergency).setOnClickListener {
            startActivity(Intent(this, EmergencyContactsActivity::class.java))
        }

        btnLogout.setOnClickListener {
            FallDetectionService.stopService(this)
            VoiceDetectionService.stopService(this)
            auth.signOut()
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val toRequest = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 100)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Permisiune necesară")
                .setMessage(
                    "Pentru ca alertele de cădere să funcționeze când aplicația " +
                            "este închisă, trebuie să dezactivezi optimizarea bateriei " +
                            "pentru această aplicație."
                )
                .setPositiveButton("Deschide setări") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Mai târziu", null)
                .show()
        }
    }

    private fun checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notifManager.canUseFullScreenIntent()) {
                AlertDialog.Builder(this)
                    .setTitle("Permisiune alertă ecran complet")
                    .setMessage(
                        "Pentru a afișa alertele de cădere pe ecran complet " +
                                "(chiar și când telefonul este blocat), trebuie să " +
                                "permiți notificări pe ecran complet."
                    )
                    .setPositiveButton("Deschide setări") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                    .setNegativeButton("Mai târziu", null)
                    .show()
            }
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Permisiune alarme exacte")
                    .setMessage(
                        "Pentru a deschide automat alertele de cădere " +
                                "pe ecran, trebuie să permiți alarme exacte."
                    )
                    .setPositiveButton("Deschide setări") { _, _ ->
                        startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:$packageName")
                            }
                        )
                    }
                    .setNegativeButton("Mai târziu", null)
                    .show()
            }
        }
    }
}