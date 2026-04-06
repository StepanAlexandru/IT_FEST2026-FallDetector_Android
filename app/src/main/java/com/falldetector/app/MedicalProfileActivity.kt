package com.falldetector.app

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MedicalProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medical_profile)

        val etConditions = findViewById<EditText>(R.id.etConditions)
        val etAllergies = findViewById<EditText>(R.id.etAllergies)
        val etMedication = findViewById<EditText>(R.id.etMedication)
        val btnSave = findViewById<AppCompatButton>(R.id.btnSave)
        val btnBack = findViewById<AppCompatButton>(R.id.btnBack)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        val uid = auth.currentUser?.uid ?: return

        // Load existing data
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                etConditions.setText(doc.getString("medicalConditions") ?: "")
                etAllergies.setText(doc.getString("allergies") ?: "")
                etMedication.setText(doc.getString("medication") ?: "")
            }

        btnSave.setOnClickListener {
            val data = mapOf(
                "medicalConditions" to etConditions.text.toString().trim(),
                "allergies" to etAllergies.text.toString().trim(),
                "medication" to etMedication.text.toString().trim()
            )

            db.collection("users").document(uid)
                .update(data)
                .addOnSuccessListener {
                    tvStatus.text = "Salvat cu succes"
                    tvStatus.setTextColor(0xFF4CAF50.toInt())
                }
                .addOnFailureListener {
                    tvStatus.text = "Eroare la salvare"
                    tvStatus.setTextColor(0xFFF44336.toInt())
                }
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}