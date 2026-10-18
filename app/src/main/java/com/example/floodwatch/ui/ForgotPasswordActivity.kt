package com.example.floodwatch.ui

import com.example.floodwatch.R
import com.example.floodwatch.data.SupabaseClient

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.floodwatch.databinding.ActivityForgotPasswordBinding
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ── Pre-fill email if passed from LoginActivity ──────────────
        val prefillEmail = intent.getStringExtra("prefill_email")
        if (!prefillEmail.isNullOrBlank()) {
            binding.editTextEmail.setText(prefillEmail)
        }

        // ── Send Reset Link ──────────────────────────────────────────
        binding.buttonSendReset.setOnClickListener {
            val email = binding.editTextEmail.text.toString().trim()

            // Validation
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Enter a valid email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Loading state
            binding.buttonSendReset.isEnabled = false
            binding.buttonSendReset.setText(R.string.sending)

            lifecycleScope.launch {
                try {
                    SupabaseClient.client.auth.resetPasswordForEmail(
                        email = email,
                        redirectUrl = SupabaseClient.RESET_PASSWORD_REDIRECT
                    )

                    // Show success card, hide form card
                    binding.cardForm.visibility = View.GONE
                    binding.layoutBackToLogin.visibility = View.GONE
                    binding.cardSuccess.visibility = View.VISIBLE
                    binding.textViewSuccessMessage.text = getString(
                        R.string.reset_link_sent_format,
                        email
                    )

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    binding.buttonSendReset.isEnabled = true
                    binding.buttonSendReset.setText(R.string.send_reset_link)

                    val errorMsg = when {
                        e.message?.contains("rate limit", ignoreCase = true) == true ->
                            "Too many requests. Please wait a few minutes and try again."
                        e.message?.contains("invalid", ignoreCase = true) == true ->
                            "Invalid email address. Please check and try again."
                        else -> "Failed to send reset link. Please try again."
                    }
                    Toast.makeText(this@ForgotPasswordActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }

        // ── Back to Login (below form card) ─────────────────────────
        binding.textViewBackToLogin.setOnClickListener {
            finish()
        }

        // ── Back to Login (inside success card) ─────────────────────
        binding.buttonBackToLoginSuccess.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }

        // ── Footer Links ─────────────────────────────────────────────
    }
}
