package com.falldetector.app

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class SignupActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val etName = findViewById<EditText>(R.id.etName)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnSignup = findViewById<AppCompatButton>(R.id.btnSignup)
        val btnGoToLogin = findViewById<AppCompatButton>(R.id.btnGoToLogin)
        val tvError = findViewById<TextView>(R.id.tvError)

        btnSignup.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                tvError.text = "Completează toate câmpurile"
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { result ->
                    val uid = result.user!!.uid
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        val user = hashMapOf(
                            "uid" to uid,
                            "name" to name,
                            "email" to email,
                            "phone" to phone,
                            "fcmToken" to token,
                            "createdAt" to System.currentTimeMillis()
                        )
                        db.collection("users").document(uid).set(user)
                            .addOnSuccessListener {
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                    }
                }
                .addOnFailureListener { tvError.text = it.message }
        }

        btnGoToLogin.setOnClickListener {
            finish()
        }
    }
}