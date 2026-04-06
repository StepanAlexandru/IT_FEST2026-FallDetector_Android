package com.falldetector.app

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class EmergencyContactsActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val contacts = mutableListOf<Map<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_contacts)

        val etName = findViewById<EditText>(R.id.etContactName)
        val etPhone = findViewById<EditText>(R.id.etContactPhone)
        val btnAdd = findViewById<AppCompatButton>(R.id.btnAddContact)
        val btnBack = findViewById<AppCompatButton>(R.id.btnBack)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        val uid = auth.currentUser?.uid ?: return

        loadContacts(uid)

        btnAdd.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty()) {
                tvStatus.text = "Completează toate câmpurile"
                tvStatus.setTextColor(0xFFF44336.toInt())
                return@setOnClickListener
            }

            val contact = mapOf("name" to name, "phone" to phone)
            contacts.add(contact)
            saveContacts(uid, tvStatus)

            etName.text.clear()
            etPhone.text.clear()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadContacts(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                try {
                    val saved = doc.get("emergencyContacts") as? List<*>
                    if (saved != null) {
                        contacts.clear()
                        for (item in saved) {
                            if (item is Map<*, *>) {
                                val name = item["name"]?.toString() ?: ""
                                val phone = item["phone"]?.toString() ?: ""
                                if (name.isNotEmpty() || phone.isNotEmpty()) {
                                    contacts.add(mapOf("name" to name, "phone" to phone))
                                }
                            }
                        }
                    }
                    renderContacts(uid)
                } catch (e: Exception) {
                    contacts.clear()
                    db.collection("users").document(uid)
                        .update("emergencyContacts", contacts)
                    renderContacts(uid)
                }
            }
    }

    private fun saveContacts(uid: String, tvStatus: TextView) {
        db.collection("users").document(uid)
            .update("emergencyContacts", contacts)
            .addOnSuccessListener {
                tvStatus.text = "Salvat"
                tvStatus.setTextColor(0xFF4CAF50.toInt())
                renderContacts(uid)
            }
            .addOnFailureListener {
                db.collection("users").document(uid)
                    .set(mapOf("emergencyContacts" to contacts), SetOptions.merge())
                    .addOnSuccessListener {
                        tvStatus.text = "Salvat"
                        tvStatus.setTextColor(0xFF4CAF50.toInt())
                        renderContacts(uid)
                    }
            }
    }

    private fun renderContacts(uid: String) {
        val container = findViewById<LinearLayout>(R.id.contactsList)
        container.removeAllViews()

        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        contacts.forEachIndexed { index, contact ->
            val view = LayoutInflater.from(this).inflate(R.layout.item_contact, container, false)
            view.findViewById<TextView>(R.id.tvContactName).text = contact["name"]
            view.findViewById<TextView>(R.id.tvContactPhone).text = contact["phone"]
            view.findViewById<TextView>(R.id.btnDelete).setOnClickListener {
                contacts.removeAt(index)
                saveContacts(uid, tvStatus)
            }
            container.addView(view)
        }
    }
}