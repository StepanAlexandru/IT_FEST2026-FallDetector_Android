package com.falldetector.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat

class VoiceDetectionService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isListening = false

    companion object {
        const val CHANNEL_ID = "voice_detection_channel"
        const val NOTIF_ID = 200

        // Cuvinte cheie care declanșează alerta
        val KEYWORDS = listOf("ajutor detector", "te rog nu mai da")

        fun startService(context: Context) {
            val intent = Intent(context, VoiceDetectionService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, VoiceDetectionService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "falldetector:voice_detection"
        )
        wakeLock?.acquire()

        startListening()
        Log.d("VoiceService", "Voice detection started!")
    }

    private fun startListening() {
        if (isListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("VoiceService", "Speech recognition not available")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                Log.d("VoiceService", "Listening...")
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d("VoiceService", "Heard: $matches")

                if (matches != null) {
                    for (result in matches) {
                        val lower = result.lowercase()
                        for (keyword in KEYWORDS) {
                            if (lower.contains(keyword)) {
                                Log.w("VoiceService", "KEYWORD DETECTED: $keyword in '$result'")
                                triggerAlert()
                                return
                            }
                        }
                    }
                }

                // Restartează ascultarea
                restartListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    for (result in matches) {
                        val lower = result.lowercase()
                        for (keyword in KEYWORDS) {
                            if (lower.contains(keyword)) {
                                Log.w("VoiceService", "KEYWORD DETECTED (partial): $keyword")
                                triggerAlert()
                                return
                            }
                        }
                    }
                }
            }

            override fun onError(error: Int) {
                isListening = false
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    else -> "Error code: $error"
                }
                Log.d("VoiceService", "Error: $errorMsg — restarting...")

                // Restartează ascultarea după o scurtă pauză
                restartListening()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ro-RO")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("VoiceService", "Start listening failed: ${e.message}")
            restartListening()
        }
    }

    private fun restartListening() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startListening()
        }, 500)
    }

    private fun triggerAlert() {
        Log.w("VoiceService", "VOICE ALERT TRIGGERED!")

        // Oprește ascultarea temporar
        speechRecognizer?.stopListening()
        isListening = false

        // Declanșează aceeași alertă ca la cădere
        FallDetectionService.triggerVoiceAlert(this)

        // Restartează ascultarea după 30 secunde (cooldown)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startListening()
        }, 30_000)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        Log.d("VoiceService", "Voice detection stopped!")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Voice Detection", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Detectare comandă vocală"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Detectare vocală activă")
            .setContentText("Spune 'Ajutor' pentru alertă")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}