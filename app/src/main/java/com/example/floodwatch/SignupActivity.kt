package com.example.floodwatch

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.floodwatch.databinding.ActivitySignupBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable



class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

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

        binding.buttonSignUp.setOnClickListener {
            val email           = binding.editTextEmail.text.toString().trim()
            val password        = binding.editTextPassword.text.toString().trim()
            val confirmPassword = binding.editTextConfirmPassword.text.toString().trim()
            val termsAccepted   = binding.checkBoxTerms.isChecked
            val fullName        = binding.editTextFullName.text.toString().trim()
            val phone           = binding.editTextPhone.text.toString().trim()

            // Validation
            if (email.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Passwords do not match!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!termsAccepted) {
                Toast.makeText(this, "Please accept the Terms of Service.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Loading state
            binding.buttonSignUp.isEnabled = false
            binding.buttonSignUp.text = "Creating account…"

            lifecycleScope.launch {
                try {
                    // STEP 1: Signup with Email
                    SupabaseClient.client.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                    }

                    // STEP 2: Kunin ang User ID (Sa v3, ito ang pinaka-reliable na paraan)
                    val userId = SupabaseClient.client.auth.currentUserOrNull()?.id

                    if (userId != null) {
                        // STEP 3: Insert sa profiles table
                        val profile = UserProfile(
                            id = userId,
                            full_name = fullName,
                            phone_number = phone,
                            address = ""
                        )

                        // Ginagamit ang postgrest extension para sa insert
                        SupabaseClient.client.from("profiles").insert(profile)

                        Toast.makeText(this@SignupActivity, "Account created! Check your email to verify.", Toast.LENGTH_LONG).show()

                        // Balik sa Login
                        startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        // Kung sakaling hindi agad nakuha ang ID (hal. email confirmation is required)
                        Toast.makeText(this@SignupActivity, "Verification email sent! Please verify first.", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                        finish()
                    }

                } catch (e: Exception) {
                    binding.buttonSignUp.isEnabled = true
                    binding.buttonSignUp.text = "Create Account  →"
                    Toast.makeText(this@SignupActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.textViewLogin.setOnClickListener {
            finish() // Balik sa Login screen
        }
    }
}