package com.falldetector.app

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth

class AuthActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<AppCompatButton>(R.id.btnLogin)
        val btnGoToSignup = findViewById<AppCompatButton>(R.id.btnGoToSignup)
        val tvError = findViewById<TextView>(R.id.tvError)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            if (email.isEmpty() || pass.isEmpty()) {
                tvError.text = "Completează email și parola"
                return@setOnClickListener
            }
            auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener { goToMain() }
                .addOnFailureListener { tvError.text = it.message }
        }

        btnGoToSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}