package com.example.floodwatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.floodwatch.databinding.ActivityLoginBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ── Handle email verification deep link ────────────────
        if (intent?.data?.host == "verify-email") {
            AlertDialog.Builder(this)
                .setTitle("Email Verified!")
                .setMessage("Your email has been verified. You can now log in to your FloodWatch account.")
                .setPositiveButton("OK", null)
                .setCancelable(false)
                .show()
        }

        // ── Pre-fill email if coming from ForgotPassword or ResetPassword ──
        val prefillEmail = intent.getStringExtra("prefill_email")
        if (!prefillEmail.isNullOrBlank()) {
            binding.editTextEmail.setText(prefillEmail)
            binding.editTextPassword.requestFocus()
        }

        // ── Login ──────────────────────────────────────────────
        binding.buttonLogin.setOnClickListener {
            val email = binding.editTextEmail.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Empty fields are not allowed!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.buttonLogin.isEnabled = false
            binding.buttonLogin.text = "Signing in…"

            lifecycleScope.launch {
                try {
                    SupabaseClient.client.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }

                    val user = SupabaseClient.client.auth.currentUserOrNull()

                    if (user?.emailConfirmedAt == null) {
                        SupabaseClient.client.auth.signOut()
                        binding.buttonLogin.isEnabled = true
                        binding.buttonLogin.text = "Login to Dashboard"
                        AlertDialog.Builder(this@LoginActivity)
                            .setTitle("Email Not Verified")
                            .setMessage("Please check your inbox and click the verification link before logging in.")
                            .setPositiveButton("OK", null)
                            .show()
                        return@launch
                    }

                    startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                    finish()

                } catch (e: Exception) {
                    binding.buttonLogin.isEnabled = true
                    binding.buttonLogin.text = "Login to Dashboard"
                    Toast.makeText(
                        this@LoginActivity,
                        "Login failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // ── Sign Up ────────────────────────────────────────────
        binding.textViewSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        // ── Forgot Password → navigate to ForgotPasswordActivity ──
        binding.textViewForgot.setOnClickListener {
            val email = binding.editTextEmail.text.toString().trim()
            val intent = Intent(this, ForgotPasswordActivity::class.java).apply {
                if (email.isNotEmpty()) {
                    putExtra("prefill_email", email)
                }
            }
            startActivity(intent)
        }

        // ── Footer Links ───────────────────────────────────────
        binding.textViewStatus.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://status.floodwatch.example.com")
                )
            )
        }

        binding.textViewPrivacy.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://floodwatch.example.com/privacy")
                )
            )
        }
    }

    override fun onStart() {
        super.onStart()

        val fromLogout = intent.getBooleanExtra("from_logout", false)
        val fromReset = !intent.getStringExtra("prefill_email").isNullOrBlank()
        val fromDeepLink = intent?.data?.host == "verify-email"
        if (fromLogout || fromReset || fromDeepLink) return

        try {
            val session = SupabaseClient.client.auth.currentSessionOrNull()
            if (session != null) {
                val user = SupabaseClient.client.auth.currentUserOrNull()

                // ── Block unverified users from auto-login ─────
                if (user?.emailConfirmedAt == null) {
                    lifecycleScope.launch {
                        try { SupabaseClient.client.auth.signOut() } catch (e: Exception) { }
                    }
                    return
                }

                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}