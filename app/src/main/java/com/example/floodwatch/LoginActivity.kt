package com.example.floodwatch

import android.content.Context // Import ito
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

        // ── Login ──────────────────────────────────────────────
        binding.buttonLogin.setOnClickListener {
            val email    = binding.editTextEmail.text.toString().trim()
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

                    // [SESSION PERSISTENCE] I-save natin na naka-login na ang user
                    val sharedPref = getSharedPreferences("FloodWatchPrefs", Context.MODE_PRIVATE)
                    with (sharedPref.edit()) {
                        putBoolean("isLoggedIn", true)
                        apply()
                    }

                    startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    binding.buttonLogin.isEnabled = true
                    binding.buttonLogin.text = "Login to Dashboard  →"
                    Toast.makeText(this@LoginActivity, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.textViewSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        binding.textViewForgot.setOnClickListener {
            val email = binding.editTextEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter your email above to reset your password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    SupabaseClient.client.auth.resetPasswordForEmail(email)
                    Toast.makeText(this@LoginActivity, "Reset link sent to $email", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.textViewStatus.setOnClickListener {
            val url = "https://status.floodwatch.example.com"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        binding.textViewPrivacy.setOnClickListener {
            val url = "https://floodwatch.example.com/privacy"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    override fun onStart() {
        super.onStart()

        // [SESSION CHECK] Gamit ang SharedPreferences para sigurado
        val sharedPref = getSharedPreferences("FloodWatchPrefs", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)

        // Check din natin sa Supabase para sure na valid ang session
        val session = SupabaseClient.client.auth.currentSessionOrNull()

        if (isLoggedIn || session != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }
}