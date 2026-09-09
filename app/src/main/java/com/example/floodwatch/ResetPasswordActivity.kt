package com.example.floodwatch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.floodwatch.databinding.ActivityResetPasswordBinding
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private var userEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Handle the deep link
        val uri = intent?.data
        Log.d("ResetPassword", "Deep link URI: $uri")

        if (uri == null) {
            Toast.makeText(this, "Invalid reset link.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Extract token from URI fragment (#access_token=...&type=recovery)
        val fragment = uri.fragment ?: ""
        val params = fragment.split("&").associate {
            val parts = it.split("=")
            parts[0] to (parts.getOrElse(1) { "" })
        }

        val accessToken = params["access_token"]
        val type = params["type"]

        Log.d("ResetPassword", "Type: $type, Token: $accessToken")

        if (type != "recovery" || accessToken.isNullOrBlank()) {
            Toast.makeText(this, "Invalid or expired reset link.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Set the session using the token
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.importAuthToken(accessToken)

                // ── Save email so we can pre-fill Login later ──────────
                userEmail = SupabaseClient.client.auth.currentUserOrNull()?.email ?: ""

                // Token is valid — show the password form
                binding.layoutLoading.visibility = View.GONE
                binding.layoutForm.visibility = View.VISIBLE

            } catch (e: Exception) {
                Log.e("ResetPassword", "Token error: ${e.message}", e)
                Toast.makeText(
                    this@ResetPasswordActivity,
                    "Reset link expired. Please request a new one.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }

        // Submit new password
        binding.buttonUpdatePassword.setOnClickListener {
            val newPass = binding.editTextNewPassword.text.toString().trim()
            val confirmPass = binding.editTextConfirmPassword.text.toString().trim()

            if (newPass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(this, "Please fill in both fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val passwordRegex = Regex("^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#\$%^&*]).{12,}\$")
            if (!passwordRegex.matches(newPass)) {
                Toast.makeText(
                    this,
                    "Password must be at least 12 characters with uppercase, number, and special character (!@#\$%^&*).",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            if (newPass != confirmPass) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.buttonUpdatePassword.isEnabled = false
            binding.buttonUpdatePassword.text = "Updating…"

            lifecycleScope.launch {
                try {
                    // ── 1. Update the password ─────────────────────────
                    SupabaseClient.client.auth.updateUser {
                        password = newPass
                    }

                    // ── 2. Sign out the recovery session ──────────────
                    try {
                        SupabaseClient.client.auth.signOut()
                    } catch (e: Exception) {
                        Log.w("ResetPassword", "Sign out warning: ${e.message}")
                    }

                    // ── 3. Show success card ───────────────────────────
                    binding.layoutForm.visibility = View.GONE
                    binding.layoutSuccess.visibility = View.VISIBLE

                    // ── 4. Auto-redirect to Login after 2 seconds ─────
                    delay(2000)
                    goToLogin()

                } catch (e: Exception) {
                    Log.e("ResetPassword", "Update error: ${e.message}", e)
                    binding.buttonUpdatePassword.isEnabled = true
                    binding.buttonUpdatePassword.text = "Update Password"
                    Toast.makeText(
                        this@ResetPasswordActivity,
                        "Failed to update password: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // Manual "Back to Login" tap (if user doesn't wait for auto-redirect)
        binding.buttonGoToLogin.setOnClickListener {
            goToLogin()
        }
    }

    // ── Navigate to LoginActivity with email pre-filled ───────────────
    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                if (userEmail.isNotBlank()) {
                    putExtra("prefill_email", userEmail)
                }
            }
        )
    }
}