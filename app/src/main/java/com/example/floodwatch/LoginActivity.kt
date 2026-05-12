package com.example.floodwatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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

                    startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                    finish()

                } catch (e: Exception) {
                    binding.buttonLogin.isEnabled = true
                    binding.buttonLogin.text = "Login to Dashboard  →"
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

        // ── Skip auto-login if coming from logout OR password reset ──
        val fromLogout = intent.getBooleanExtra("from_logout", false)
        val fromReset = !intent.getStringExtra("prefill_email").isNullOrBlank()
        if (fromLogout || fromReset) return

        try {
            val session = SupabaseClient.client.auth.currentSessionOrNull()
            if (session != null) {
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}