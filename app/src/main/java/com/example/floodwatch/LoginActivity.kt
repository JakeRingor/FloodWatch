package com.example.floodwatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.floodwatch.databinding.ActivityLoginBinding
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        firebaseAuth     = FirebaseAuth.getInstance()
        firebaseAnalytics = Firebase.analytics

        // ── Login ──────────────────────────────────────────────
        binding.buttonLogin.setOnClickListener {
            val email    = binding.editTextEmail.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Empty fields are not allowed!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show a loading state on the button
            binding.buttonLogin.isEnabled = false
            binding.buttonLogin.text = "Signing in…"

            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->

                    // Reset button regardless of outcome
                    binding.buttonLogin.isEnabled = true
                    binding.buttonLogin.text = "Login to Dashboard  →"

                    if (task.isSuccessful) {
                        // Log analytics event
                        val bundle = Bundle().apply {
                            putString(FirebaseAnalytics.Param.METHOD, "email_password")
                        }
                        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, bundle)

                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()

                    } else {
                        when (val exception = task.exception) {
                            is FirebaseAuthInvalidUserException ->
                                Toast.makeText(
                                    this,
                                    "Account does not exist. Please sign up.",
                                    Toast.LENGTH_SHORT
                                ).show()

                            is FirebaseAuthInvalidCredentialsException ->
                                Toast.makeText(
                                    this,
                                    "Incorrect password. Please try again.",
                                    Toast.LENGTH_SHORT
                                ).show()

                            else ->
                                Toast.makeText(
                                    this,
                                    "Login failed: ${exception?.localizedMessage}",
                                    Toast.LENGTH_SHORT
                                ).show()
                        }
                    }
                }
        }

        // ── Sign Up ────────────────────────────────────────────
        binding.textViewSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        // ── Forgot Password ────────────────────────────────────
        binding.textViewForgot.setOnClickListener {
            val email = binding.editTextEmail.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "Enter your email above to reset your password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Reset link sent to $email", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Failed: ${task.exception?.localizedMessage}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        // ── Keep Me Signed In ──────────────────────────────────
        // (checkbox state is read-only here; use SharedPreferences if you
        //  want to persist the preference across launches)
        binding.checkBoxKeepSigned.setOnCheckedChangeListener { _, isChecked ->
            val msg = if (isChecked) "You'll stay signed in." else "You'll be signed out on exit."
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // ── Footer: System Status ──────────────────────────────
        binding.textViewStatus.setOnClickListener {
            // Replace with your real status-page URL
            val url = "https://status.floodwatch.example.com"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        // ── Footer: Privacy Policy ─────────────────────────────
        binding.textViewPrivacy.setOnClickListener {
            // Replace with your real privacy-policy URL
            val url = "https://floodwatch.example.com/privacy"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    // ── Auto-login if session is still active ──────────────────
    override fun onStart() {
        super.onStart()
        if (firebaseAuth.currentUser != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }
}