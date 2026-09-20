package com.example.floodwatch

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.floodwatch.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var currentProfile: UserProfile? = null
    private var submittedCount = 0
    private var verifiedCount = 0
    private var lastStatisticsLoadAt = 0L

    private val profilePhotoPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) uploadProfilePhoto(uri) }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && _binding != null) {
            Snackbar.make(
                binding.root,
                "Notifications are blocked. You can enable them in App settings.",
                Snackbar.LENGTH_LONG
            ).setAction("Settings") { openAppSettings() }.show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.profileImage.setOnClickListener { chooseProfilePhoto() }
        binding.profileEditButton.setOnClickListener { chooseProfilePhoto() }
        binding.editProfile.setOnClickListener { showEditProfileDialog() }
        binding.changePassword.setOnClickListener { confirmPasswordReset() }
        binding.logoutBtn.setOnClickListener { confirmLogout() }
        binding.submittedReportsCard.setOnClickListener {
            (activity as? HomeActivity)?.openMyReports(verifiedOnly = false)
        }
        binding.verifiedReportsCard.setOnClickListener {
            (activity as? HomeActivity)?.openMyReports(verifiedOnly = true)
        }
        binding.emergencyContactRow.setOnClickListener { showEmergencyContactActions() }
        binding.homeZoneRow.setOnClickListener { openHomeZoneInMaps() }

        setupNotificationPreferences()
        loadProfileAndStatistics()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) loadReportStatistics()
    }

    private fun loadProfileAndStatistics() {
        val user = SupabaseClient.client.auth.currentUserOrNull()
        if (user == null) {
            redirectToLoginForExpiredSession()
            return
        }

        binding.textViewUserEmail.text = user.email
        binding.textViewUserName.text = user.userMetadata
            ?.get("full_name")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: "FloodWatch User"

        val remoteAvatar = user.userMetadata
            ?.get("avatar_url")?.jsonPrimitive?.contentOrNull
        if (!remoteAvatar.isNullOrBlank()) displayProfilePhoto(remoteAvatar)
        else loadSavedProfilePhoto()

        binding.profileLoading.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                currentProfile = SupabaseClient.client
                    .from("user_profiles")
                    .select { filter { eq("id", user.id) } }
                    .decodeList<UserProfile>().firstOrNull()
                currentProfile?.fullName?.takeIf { it.isNotBlank() }?.let {
                    if (_binding != null) binding.textViewUserName.text = it
                }
                currentProfile?.address?.takeIf { it.isNotBlank() }?.let {
                    if (_binding != null) binding.textViewHomeZoneValue.text = it
                }
                currentProfile?.profileImageUrl?.takeIf { it.isNotBlank() }?.let {
                    if (_binding != null) displayProfilePhoto(it)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showRetryMessage("Unable to refresh profile") { loadProfileAndStatistics() }
            } finally {
                if (_binding != null) binding.profileLoading.visibility = View.GONE
            }
        }

        loadReportStatistics()
        loadEmergencyContact()
    }

    private fun loadReportStatistics() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastStatisticsLoadAt < 1_000L) return
        lastStatisticsLoadAt = now
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
            ?: SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
            ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val allReportsResult = SupabaseClient.client
                    .from("flood_reports")
                    .select {
                        head = true
                        count(Count.EXACT)
                        filter { eq("user_id", userId) }
                    }
                val verifiedReportsResult = SupabaseClient.client
                    .from("flood_reports")
                    .select {
                        head = true
                        count(Count.EXACT)
                        filter {
                            eq("user_id", userId)
                            ilike("status", "verified")
                        }
                    }
                submittedCount = allReportsResult.countOrNull()?.toInt() ?: 0
                verifiedCount = verifiedReportsResult.countOrNull()?.toInt() ?: 0
                if (_binding != null) {
                    binding.textViewReportsSubmittedCount.text = submittedCount.toString()
                    binding.textViewVerifiedCount.text = verifiedCount.toString()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (_binding != null) {
                    binding.textViewReportsSubmittedCount.text = "--"
                    binding.textViewVerifiedCount.text = "--"
                    showRetryMessage("Unable to load report statistics") { loadReportStatistics() }
                }
            }
        }
    }

    private fun setupNotificationPreferences() {
        val prefs = profilePreferences()
        binding.switchFloodAlerts.isChecked = prefs.getBoolean(PREF_FLOOD_ALERTS, true)

        binding.switchFloodAlerts.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean(PREF_FLOOD_ALERTS, enabled).apply()
            if (enabled) requestNotificationPermissionIfNeeded()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun showEditProfileDialog() {
        val user = SupabaseClient.client.auth.currentUserOrNull()
        if (user == null) {
            Toast.makeText(requireContext(), "Please sign in again", Toast.LENGTH_SHORT).show()
            return
        }

        val container = dialogForm()
        val nameInput = dialogInput("Full name").apply {
            setText(currentProfile?.fullName ?: binding.textViewUserName.text)
        }
        val phoneInput = dialogInput("Phone number").apply {
            inputType = InputType.TYPE_CLASS_PHONE
            setText(currentProfile?.phoneNumber.orEmpty())
        }
        val addressInput = dialogInput("Address").apply {
            setText(currentProfile?.address.orEmpty())
        }
        container.addView(nameInput)
        container.addView(phoneInput)
        container.addView(addressInput)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Profile").setView(container)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                val address = addressInput.text.toString().trim()
                if (name.isBlank()) {
                    nameInput.error = "Name is required"
                    return@setOnClickListener
                }
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val updated = UserProfile(
                            id = user.id,
                            fullName = name,
                            phoneNumber = phone.ifBlank { null },
                            address = address.ifBlank { null },
                            profileImageUrl = currentProfile?.profileImageUrl,
                            createdAt = currentProfile?.createdAt
                        )
                        SupabaseClient.client.from("user_profiles").upsert(updated)
                        SupabaseClient.client.auth.updateUser { data { put("full_name", name) } }
                        currentProfile = updated
                        if (_binding != null) {
                            binding.textViewUserName.text = name
                            binding.textViewHomeZoneValue.text =
                                updated.address ?: getString(R.string.home_zone_address)
                        }
                        dialog.dismiss()
                        Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (error: Exception) {
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        Toast.makeText(requireContext(), "Could not update profile: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmPasswordReset() {
        val email = SupabaseClient.client.auth.currentUserOrNull()?.email
        if (email.isNullOrBlank()) {
            Toast.makeText(requireContext(), "No email is linked to this account", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Password")
            .setMessage("Send a password reset link to $email?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send") { _, _ -> sendPasswordReset(email) }.show()
    }

    private fun sendPasswordReset(email: String) {
        binding.changePassword.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.resetPasswordForEmail(
                    email = email,
                    redirectUrl = "com.example.floodwatch://reset-password"
                )
                Toast.makeText(requireContext(), "Reset link sent to $email", Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Unable to send reset link", Toast.LENGTH_LONG).show()
            } finally {
                if (_binding != null) binding.changePassword.isEnabled = true
            }
        }
    }

    private fun showEmergencyContactActions() {
        val prefs = profilePreferences()
        val name = prefs.getString(PREF_EMERGENCY_NAME, DEFAULT_EMERGENCY_NAME)!!
        val phone = prefs.getString(PREF_EMERGENCY_PHONE, "").orEmpty()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Emergency Contact")
            .setItems(arrayOf("Call $name", "Send SMS", "Edit contact")) { _, which ->
                when (which) {
                    0 -> if (phone.isBlank()) promptToAddContact() else
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")))
                    1 -> if (phone.isBlank()) promptToAddContact() else
                        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}")))
                    2 -> showEditEmergencyContactDialog(name, phone)
                }
            }.show()
    }

    private fun promptToAddContact() {
        Toast.makeText(requireContext(), "Add a phone number first", Toast.LENGTH_SHORT).show()
        showEditEmergencyContactDialog(
            profilePreferences().getString(PREF_EMERGENCY_NAME, DEFAULT_EMERGENCY_NAME)!!, ""
        )
    }

    private fun showEditEmergencyContactDialog(currentName: String, currentPhone: String) {
        val container = dialogForm()
        val nameInput = dialogInput("Contact name").apply { setText(currentName) }
        val phoneInput = dialogInput("Phone number").apply {
            inputType = InputType.TYPE_CLASS_PHONE
            setText(currentPhone)
        }
        container.addView(nameInput)
        container.addView(phoneInput)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Emergency Contact").setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim().ifBlank { DEFAULT_EMERGENCY_NAME }
                val phone = phoneInput.text.toString().trim()
                profilePreferences().edit()
                    .putString(PREF_EMERGENCY_NAME, name)
                    .putString(PREF_EMERGENCY_PHONE, phone).apply()
                loadEmergencyContact()
                Toast.makeText(requireContext(), "Emergency contact saved", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun loadEmergencyContact() {
        val prefs = profilePreferences()
        val name = prefs.getString(PREF_EMERGENCY_NAME, DEFAULT_EMERGENCY_NAME)!!
        val phone = prefs.getString(PREF_EMERGENCY_PHONE, "").orEmpty()
        binding.textViewEmergencyContactName.text = if (phone.isBlank()) name else "$name • $phone"
    }

    private fun openHomeZoneInMaps() {
        val savedAddress = currentProfile?.address?.trim().orEmpty()
        val defaultLabel = "${AppLocation.BARANGAY}, ${AppLocation.MUNICIPALITY}, ${AppLocation.PROVINCE}"
        val mapIntent = if (savedAddress.isNotBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(savedAddress)}"))
        } else {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:${AppLocation.LAT},${AppLocation.LNG}?q=${Uri.encode(defaultLabel)}")
            )
        }
        if (mapIntent.resolveActivity(requireContext().packageManager) != null) startActivity(mapIntent)
        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://www.google.com/maps/search/?api=1&query=${Uri.encode(savedAddress.ifBlank { defaultLabel })}"
        )))
    }

    private fun confirmLogout() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Log out?")
            .setMessage("Are you sure you want to log out of FloodWatch?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ -> performLogout() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setTextColor(android.graphics.Color.parseColor("#D32F2F"))
        }
        dialog.show()
    }

    private fun performLogout() {
        binding.logoutBtn.isEnabled = false
        binding.logoutBtn.text = "Signing out…"
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A network failure must not leave a persistent local session behind.
                runCatching { SupabaseClient.client.auth.clearSession() }
            }
            if (!isAdded) return@launch
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("from_logout", true)
            })
            requireActivity().finish()
        }
    }

    private fun chooseProfilePhoto() = profilePhotoPicker.launch(
        arrayOf("image/jpeg", "image/png", "image/webp")
    )

    private fun uploadProfilePhoto(uri: Uri) {
        val resolver = requireContext().contentResolver
        val mimeType = resolver.getType(uri).orEmpty().lowercase()
        val extension = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> null
        }
        if (extension == null) {
            Toast.makeText(
                requireContext(),
                "Please select a JPEG, PNG, or WebP image",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val fileSize = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (fileSize > MAX_PROFILE_PHOTO_BYTES) {
            Toast.makeText(requireContext(), "Profile photo must be 5 MB or smaller", Toast.LENGTH_LONG).show()
            return
        }
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) { }
        val user = SupabaseClient.client.auth.currentUserOrNull()
        if (user == null) {
            Toast.makeText(requireContext(), "Please sign in again", Toast.LENGTH_SHORT).show()
            return
        }
        setPhotoLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Unable to read selected image")
                val path = "${user.id}/avatar.$extension"
                val bucket = SupabaseClient.client.storage.from(PROFILE_BUCKET)
                bucket.upload(path, bytes) {
                    upsert = true
                    contentType = ContentType.parse(mimeType)
                }
                val avatarUrl = bucket.publicUrl(path) + "?v=${System.currentTimeMillis()}"
                SupabaseClient.client.auth.updateUser { data { put("avatar_url", avatarUrl) } }
                SupabaseClient.client.from("user_profiles").update({
                    set("profile_image_url", avatarUrl)
                }) {
                    filter { eq("id", user.id) }
                }
                currentProfile = currentProfile?.copy(profileImageUrl = avatarUrl)
                profilePreferences().edit()
                    .putString(PROFILE_PHOTO_URI, uri.toString())
                    .apply()
                if (_binding != null) {
                    displayProfilePhoto(avatarUrl)
                    Toast.makeText(requireContext(), "Profile photo updated", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                if (_binding != null) Toast.makeText(
                    requireContext(), "Upload failed: ${error.message}", Toast.LENGTH_LONG
                ).show()
            } finally {
                if (_binding != null) setPhotoLoading(false)
            }
        }
    }

    private fun setPhotoLoading(loading: Boolean) {
        binding.profileLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.profileEditButton.isEnabled = !loading
        binding.profileImage.isEnabled = !loading
        binding.profileImage.alpha = if (loading) 0.6f else 1f
    }

    private fun loadSavedProfilePhoto() {
        profilePreferences().getString(PROFILE_PHOTO_URI, null)
            ?.let(Uri::parse)?.let(::displayProfilePhoto)
    }

    private fun displayProfilePhoto(source: Any) {
        if (_binding == null) return
        Glide.with(this).load(source).circleCrop()
            .placeholder(R.drawable.ic_boy).error(R.drawable.ic_boy).into(binding.profileImage)
    }

    private fun showRetryMessage(message: String, retry: () -> Unit) {
        if (_binding != null) Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Retry") { retry() }.show()
    }

    private fun dialogForm() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        val horizontal = (24 * resources.displayMetrics.density).toInt()
        val vertical = (8 * resources.displayMetrics.density).toInt()
        setPadding(horizontal, vertical, horizontal, 0)
    }

    private fun dialogInput(hintText: String) = EditText(requireContext()).apply {
        hint = hintText
        setTextColor(android.graphics.Color.parseColor("#0F2040"))
        setHintTextColor(android.graphics.Color.parseColor("#7890A6"))
        backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor("#2563EB")
        )
        setSingleLine(true)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun profilePreferences(): android.content.SharedPreferences {
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
            ?: SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
            ?: "guest"
        val scopedPreferences = requireContext().getSharedPreferences(
            "${PROFILE_PREFS}_$userId",
            0
        )

        // Move preferences saved by older builds to the account that is currently
        // signed in. Future accounts receive their own isolated preference file.
        val legacyPreferences = requireContext().getSharedPreferences(PROFILE_PREFS, 0)
        if (scopedPreferences.all.isEmpty() && legacyPreferences.all.isNotEmpty()) {
            scopedPreferences.edit()
                .putString(
                    PROFILE_PHOTO_URI,
                    legacyPreferences.getString(PROFILE_PHOTO_URI, null)
                )
                .putBoolean(
                    PREF_FLOOD_ALERTS,
                    legacyPreferences.getBoolean(PREF_FLOOD_ALERTS, true)
                )
                .putString(
                    PREF_EMERGENCY_NAME,
                    legacyPreferences.getString(PREF_EMERGENCY_NAME, DEFAULT_EMERGENCY_NAME)
                )
                .putString(
                    PREF_EMERGENCY_PHONE,
                    legacyPreferences.getString(PREF_EMERGENCY_PHONE, "")
                )
                .apply()
            legacyPreferences.edit().clear().apply()
        }
        return scopedPreferences
    }

    private fun openAppSettings() = startActivity(Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${requireContext().packageName}")
    ))

    private fun redirectToLoginForExpiredSession() {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { SupabaseClient.client.auth.clearSession() }
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val PROFILE_PREFS = "profile_preferences"
        private const val PROFILE_PHOTO_URI = "profile_photo_uri"
        private const val PROFILE_BUCKET = "profile-images"
        private const val PREF_FLOOD_ALERTS = "notify_flood_alerts"
        private const val PREF_EMERGENCY_NAME = "emergency_contact_name"
        private const val PREF_EMERGENCY_PHONE = "emergency_contact_phone"
        private const val DEFAULT_EMERGENCY_NAME = "Family or Friends"
        private const val MAX_PROFILE_PHOTO_BYTES = 5L * 1024L * 1024L
    }
}
