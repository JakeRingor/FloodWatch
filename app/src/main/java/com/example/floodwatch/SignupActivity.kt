package com.example.floodwatch

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.TextPaint
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.floodwatch.databinding.ActivitySignupBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, keyboard.bottom)
            )
            insets
        }

        // Keep the lower password fields above the on-screen keyboard.
        binding.editTextConfirmPassword.setOnFocusChangeListener { field, hasFocus ->
            if (hasFocus) {
                field.postDelayed({
                    field.requestRectangleOnScreen(
                        android.graphics.Rect(0, 0, field.width, field.height),
                        true
                    )
                }, 250L)
            }
        }

        setupLegalLinks()

        binding.buttonSignUp.setOnClickListener {
            val email           = binding.editTextEmail.text.toString().trim()
            val password        = binding.editTextPassword.text.toString().trim()
            val confirmPassword = binding.editTextConfirmPassword.text.toString().trim()
            val termsAccepted   = binding.checkBoxTerms.isChecked
            val fullName        = binding.editTextFullName.text.toString().trim()
            val phone           = binding.editTextPhone.text.toString().trim()

            // ── Validation ─────────────────────────────────────────
            if (email.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Enter a valid email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!PasswordRules.isValid(password)) {
                Toast.makeText(this, PasswordRules.ERROR_MESSAGE, Toast.LENGTH_LONG).show()
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

            // ── Loading State ──────────────────────────────────────
            binding.buttonSignUp.isEnabled = false
            binding.buttonSignUp.text = "Creating account…"

            lifecycleScope.launch {
                try {
                    SupabaseClient.client.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                        data = buildJsonObject {
                            put("full_name", fullName)
                            put("phone_number", phone)
                            put("terms_version", LegalPolicy.TERMS_VERSION)
                            put("privacy_version", LegalPolicy.PRIVACY_VERSION)
                            put("legal_accepted_at", Instant.now().toString())
                        }
                    }

                    // ── Show Verification Dialog ───────────────────
                    AlertDialog.Builder(this@SignupActivity)
                        .setTitle("Verify Your Email")
                        .setMessage("A verification link has been sent to $email.\n\nPlease check your inbox and click the link before logging in.")
                        .setPositiveButton("Go to Login") { _, _ ->
                            startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                            finish()
                        }
                        .setCancelable(false)
                        .show()

                } catch (e: Exception) {
                    binding.buttonSignUp.isEnabled = true
                    binding.buttonSignUp.text = "Create Account"
                    Toast.makeText(this@SignupActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.textViewLogin.setOnClickListener {
            finish()
        }
    }

    private fun setupLegalLinks() {
        val text = "I agree to the Terms of Service and Privacy Policy"
        val styled = SpannableString(text)
        styled.setSpan(
            legalLink { startActivity(LegalDocumentActivity.termsIntent(this)) },
            text.indexOf("Terms of Service"),
            text.indexOf("Terms of Service") + "Terms of Service".length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        styled.setSpan(
            legalLink { startActivity(LegalDocumentActivity.privacyIntent(this)) },
            text.indexOf("Privacy Policy"),
            text.indexOf("Privacy Policy") + "Privacy Policy".length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.textViewLegalAgreement.text = styled
        binding.textViewLegalAgreement.movementMethod = LinkMovementMethod.getInstance()
        binding.textViewLegalAgreement.highlightColor = android.graphics.Color.TRANSPARENT
    }

    private fun legalLink(action: () -> Unit) = object : ClickableSpan() {
        override fun onClick(widget: View) = action()

        override fun updateDrawState(drawState: TextPaint) {
            super.updateDrawState(drawState)
            drawState.color = android.graphics.Color.parseColor("#2563EB")
            drawState.isUnderlineText = true
            drawState.isFakeBoldText = true
        }
    }
}
