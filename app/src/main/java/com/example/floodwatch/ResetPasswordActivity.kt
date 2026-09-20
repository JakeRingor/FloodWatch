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
import io.github.jan.supabase.auth.parseSessionFromUrl
import kotlinx.coroutines.CancellationException
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

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val uri = intent?.data
        Log.d(TAG, "Deep link received: ${uri?.scheme}://${uri?.host}")

        if (uri == null) {
            showInvalidLinkAndClose()
            return
        }

        // Import the complete recovery session. The URL includes a refresh token,
        // expiry, token type, and recovery type in addition to the access token.
        lifecycleScope.launch {
            try {
                val auth = SupabaseClient.client.auth
                val session = auth.parseSessionFromUrl(uri.toString())

                if (session.type != "recovery") {
                    throw IllegalArgumentException("The link is not a recovery link")
                }

                // This session is short-lived and is needed only for updateUser.
                auth.stopAutoRefreshForCurrentSession()
                auth.importSession(session, autoRefresh = false)

                // Verify the token before allowing the password to be submitted.
                userEmail = auth.retrieveUserForCurrentSession().email.orEmpty()

                binding.layoutLoading.visibility = View.GONE
                binding.layoutForm.visibility = View.VISIBLE
            } catch (e: CancellationException) {
                // Normal when the Activity is destroyed; do not display this as an error.
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recovery session error", e)
                showInvalidLinkAndClose()
            }
        }

        binding.buttonUpdatePassword.setOnClickListener {
            updatePassword()
        }

        binding.buttonGoToLogin.setOnClickListener {
            goToLogin()
        }
    }

    private fun updatePassword() {
        val newPassword = binding.editTextNewPassword.text.toString()
        val confirmedPassword = binding.editTextConfirmPassword.text.toString()

        if (newPassword.isBlank() || confirmedPassword.isBlank()) {
            Toast.makeText(this, "Please fill in both fields.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!PasswordRules.isValid(newPassword)) {
            Toast.makeText(
                this,
                PasswordRules.ERROR_MESSAGE,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (newPassword != confirmedPassword) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
            return
        }

        setUpdating(true)

        lifecycleScope.launch {
            try {
                val auth = SupabaseClient.client.auth

                // Uses the verified recovery session imported above.
                auth.updateUser {
                    password = newPassword
                }

                // The update already succeeded. A network signOut here can cancel
                // an auth job and incorrectly turn success into "Job was cancelled".
                runCatching { auth.clearSession() }
                    .onFailure { Log.w(TAG, "Could not clear local recovery session", it) }

                binding.layoutForm.visibility = View.GONE
                binding.layoutSuccess.visibility = View.VISIBLE

                delay(2000)
                goToLogin()
            } catch (e: CancellationException) {
                // lifecycleScope cancellation is not an update failure.
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Password update error", e)
                setUpdating(false)
                Toast.makeText(
                    this@ResetPasswordActivity,
                    readableUpdateError(e),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setUpdating(updating: Boolean) {
        binding.buttonUpdatePassword.isEnabled = !updating
        binding.buttonUpdatePassword.text = if (updating) "Updating..." else "Update Password"
    }

    private fun readableUpdateError(error: Exception): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("expired", ignoreCase = true) ||
                message.contains("jwt", ignoreCase = true) ||
                message.contains("session", ignoreCase = true) ->
                "The reset link has expired. Please request a new one."
            message.contains("weak", ignoreCase = true) ||
                message.contains("password", ignoreCase = true) ->
                "That password is not accepted. Please choose a stronger password."
            else -> "Failed to update password. Please check your connection and try again."
        }
    }

    private fun showInvalidLinkAndClose() {
        Toast.makeText(
            this,
            "The reset link is invalid or expired. Please request a new one.",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

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

    private companion object {
        const val TAG = "ResetPassword"
    }
}
