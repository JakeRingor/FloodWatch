package com.example.floodwatch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.floodwatch.databinding.ActivityLegalDocumentBinding

object LegalPolicy {
    const val TERMS_VERSION = "1.0"
    const val PRIVACY_VERSION = "1.0"
}

class LegalDocumentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLegalDocumentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLegalDocumentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val privacy = intent.getStringExtra(EXTRA_DOCUMENT) == DOCUMENT_PRIVACY
        binding.legalToolbar.title = if (privacy) "Privacy Policy" else "Terms of Service"
        binding.legalBody.text = if (privacy) PRIVACY_TEXT else TERMS_TEXT
        binding.legalToolbar.setNavigationOnClickListener { finish() }
        binding.legalCloseButton.setOnClickListener { finish() }
    }

    companion object {
        private const val EXTRA_DOCUMENT = "legal_document"
        private const val DOCUMENT_TERMS = "terms"
        private const val DOCUMENT_PRIVACY = "privacy"

        fun termsIntent(context: Context) =
            Intent(context, LegalDocumentActivity::class.java)
                .putExtra(EXTRA_DOCUMENT, DOCUMENT_TERMS)

        fun privacyIntent(context: Context) =
            Intent(context, LegalDocumentActivity::class.java)
                .putExtra(EXTRA_DOCUMENT, DOCUMENT_PRIVACY)

        private val TERMS_TEXT = """
            Version ${LegalPolicy.TERMS_VERSION}
            Last updated: September 20, 2026

            1. Acceptance
            By creating an account or using FloodWatch, you agree to these Terms of Service and the Privacy Policy.

            2. Purpose of FloodWatch
            FloodWatch provides community flood reporting, verified incident information, weather layers, risk indicators, and safety-related alerts. Information may be delayed, incomplete, or inaccurate.

            3. Emergency disclaimer
            FloodWatch is not a replacement for official government warnings, emergency hotlines, rescue services, or professional safety advice. In an emergency, contact the proper authorities and follow official evacuation instructions.

            4. Your account
            You are responsible for providing accurate registration details, protecting your password, and activities performed through your account. Do not share access with unauthorized persons.

            5. Flood reports and uploaded content
            Submit only genuine, current, and relevant flood information. You must have permission to upload any photo or content you provide. Submitted reports may be reviewed, verified, dismissed, or removed by authorized administrators.

            6. Prohibited conduct
            You must not submit false reports, impersonate another person, harass others, upload harmful or illegal content, interfere with the service, or attempt unauthorized access.

            7. Third-party services
            FloodWatch may rely on services such as Supabase, Google Maps, and OpenWeather. Their availability and data accuracy are outside FloodWatch's direct control.

            8. Suspension and termination
            Access may be limited or terminated for misuse, false reporting, security threats, or violations of these terms.

            9. Service changes
            Features and these terms may be updated to improve safety, security, or legal compliance. Material changes should be presented with an updated version and date.

            10. Contact
            Questions or account-related requests may be sent through the official FloodWatch support channel provided by the project administrators.
        """.trimIndent()

        private val PRIVACY_TEXT = """
            Version ${LegalPolicy.PRIVACY_VERSION}
            Last updated: September 20, 2026

            1. Information collected
            FloodWatch may collect your name, email address, phone number, profile photo, saved address, emergency contact, account identifiers, device notification preference, and flood reports you submit.

            2. Location and report information
            With permission, the app may use device location and elevation to show your position and support location-based flood features. Reports may contain coordinates, an address, flood conditions, descriptions, timestamps, and uploaded photos.

            3. How information is used
            Information is used to create and secure accounts, display account-specific profiles, process and verify flood reports, provide maps and weather information, send alerts, prevent abuse, and improve service reliability.

            4. Service providers
            FloodWatch uses service providers that may process limited information needed to operate the app, including Supabase for authentication, database, and storage; Google Maps for mapping; and OpenWeather for weather information.

            5. Sharing
            Personal information is not sold. Verified report information may be displayed to users when needed for public flood awareness. Information may also be disclosed when required by law or necessary to protect users and service security.

            6. Storage and retention
            Account and report data is retained only as reasonably needed to provide the service, maintain safety records, resolve disputes, prevent abuse, and meet applicable obligations. Some local preferences remain on the device until removed or the app data is cleared.

            7. Security
            Reasonable technical safeguards and account-based access controls are used. No internet-connected system can guarantee absolute security.

            8. Your choices and rights
            You may edit profile information, manage notification preferences, withdraw eligible reports, and request access, correction, or deletion through the official FloodWatch support channel. Some records may be retained when legally or operationally required.

            9. Children's privacy
            Users who are not legally able to consent should use the service only with permission and guidance from a parent or legal guardian.

            10. Policy updates
            This policy may be revised when features or data practices change. The version and last-updated date identify the policy that applies.

            11. Contact
            Privacy questions and data requests may be sent through the official FloodWatch support channel provided by the project administrators.
        """.trimIndent()
    }
}
