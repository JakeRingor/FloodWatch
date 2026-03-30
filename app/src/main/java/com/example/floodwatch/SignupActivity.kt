package com.example.floodwatch

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.floodwatch.databinding.ActivitySignupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.userProfileChangeRequest

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        firebaseAuth = FirebaseAuth.getInstance()

        // ── Sign Up ────────────────────────────────────────────
        binding.buttonSignUp.setOnClickListener {
            val email           = binding.editTextEmail.text.toString().trim()
            val password        = binding.editTextPassword.text.toString().trim()
            val confirmPassword = binding.editTextConfirmPassword.text.toString().trim()
            val termsAccepted   = binding.checkBoxTerms.isChecked

            // ── Validation (same logic as your original + extras) ──
            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Empty Fields Are not Allowed !!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Enter a valid email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Password is not matching", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!termsAccepted) {
                Toast.makeText(this, "Please accept the Terms of Service to continue.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ── Loading state ──
            binding.buttonSignUp.isEnabled = false
            binding.buttonSignUp.text = "Creating account…"

            // ── Firebase create user (same as your original) ──
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->

                    if (task.isSuccessful) {
                        // Send email verification
                        firebaseAuth.currentUser?.sendEmailVerification()

                        Toast.makeText(
                            this,
                            "Account created! Please verify your email before logging in.",
                            Toast.LENGTH_LONG
                        ).show()

                        // Sign out so they must verify email first
                        firebaseAuth.signOut()

                        // Go back to Login (same as your original)
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                        finish()

                    } else {
                        // Reset button on failure
                        binding.buttonSignUp.isEnabled = true
                        binding.buttonSignUp.text = "Create Account  →"

                        // Better error messages than just exception.toString()
                        when (val exception = task.exception) {
                            is FirebaseAuthUserCollisionException ->
                                Toast.makeText(
                                    this,
                                    "An account with this email already exists.",
                                    Toast.LENGTH_SHORT
                                ).show()

                            is FirebaseAuthWeakPasswordException ->
                                Toast.makeText(
                                    this,
                                    "Password is too weak. Please use a stronger one.",
                                    Toast.LENGTH_SHORT
                                ).show()

                            else ->
                                Toast.makeText(
                                    this,
                                    exception.toString(),  // same as your original
                                    Toast.LENGTH_SHORT
                                ).show()
                        }
                    }
                }
        }

        // ── Already have account → Login (same as your original) ──
        binding.textViewLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }
}