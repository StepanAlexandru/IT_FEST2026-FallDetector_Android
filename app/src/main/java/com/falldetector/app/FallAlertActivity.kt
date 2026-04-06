package com.falldetector.app

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class FallAlertActivity : AppCompatActivity() {

    private var countDownTimer: CountDownTimer? = null
    private var alertHandled = false

    companion object {
        const val NOTIFICATION_ID = 999
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
        notifManager.cancel(NOTIFICATION_ID)

        setContentView(R.layout.activity_fall_alert)

        startCountdown()

        findViewById<AppCompatButton>(R.id.btnImOk).setOnClickListener {
            alertHandled = true
            countDownTimer?.cancel()
            FallDetectionService.cancelAlert(this)
            finish()
        }

        findViewById<AppCompatButton>(R.id.btnSendAlert).setOnClickListener {
            alertHandled = true
            countDownTimer?.cancel()
            FallDetectionService.triggerFallAlert(this)
            finish()
        }
    }

    private fun startCountdown() {
        val tvCountdown = findViewById<TextView>(R.id.tvCountdown)

        val elapsed = System.currentTimeMillis() - FallDetectionService.fallDetectedAt
        val remaining = (15000 - elapsed).coerceAtLeast(0)

        if (remaining <= 0) {
            finish()
            return
        }

        countDownTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvCountdown.text = "${millisUntilFinished / 1000}"
            }

            override fun onFinish() {
                tvCountdown.text = "0"
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        if (!alertHandled) {
            // Activity closed without user action — just stop alarm sound
            // Countdown in service continues and will send automatically
            FallDetectionService.stopAlarmStatic(this)
        }
    }
}