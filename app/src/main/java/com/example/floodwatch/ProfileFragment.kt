package com.example.floodwatch

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import io.github.jan.supabase.storage.storage
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
            (activity as? HomeActivity)?.openMyReports()
        }
        binding.verifiedReportsCard.setOnClickListener {
            (activity as? HomeActivity)?.openMyReports()
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
            binding.textViewUserName.text = "FloodWatch User"
            binding.textViewUserEmail.text = "Session expired"
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
        lifecycleScope.launch {
            try {
                currentProfile = SupabaseClient.client
                    .from("user_profiles")
                    .select { filter { eq("id", user.id) } }
                    .decodeList<UserProfile>().firstOrNull()
                currentProfile?.fullName?.takeIf { it.isNotBlank() }?.let {
                    if (_binding != null) binding.textViewUserName.text = it
                }
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
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
            ?: SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
            ?: return
        lifecycleScope.launch {
            try {
                val reports = SupabaseClient.client
                    .from("flood_reports")
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<FloodReport>()
                submittedCount = reports.size
                verifiedCount = reports.count { it.status.equals("VERIFIED", ignoreCase = true) }
                if (_binding != null) {
                    binding.textViewReportsSubmittedCount.text = submittedCount.toString()
                    binding.textViewVerifiedCount.text = verifiedCount.toString()
                }
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
        binding.switchWaterLevel.isChecked = prefs.getBoolean(PREF_WATER_LEVEL, true)
        binding.switchCommunityNews.isChecked = prefs.getBoolean(PREF_COMMUNITY_NEWS, false)

        binding.switchFloodAlerts.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean(PREF_FLOOD_ALERTS, enabled).apply()
            if (enabled) requestNotificationPermissionIfNeeded()
        }
        binding.switchWaterLevel.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean(PREF_WATER_LEVEL, enabled).apply()
            if (enabled) requestNotificationPermissionIfNeeded()
        }
        binding.switchCommunityNews.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean(PREF_COMMUNITY_NEWS, enabled).apply()
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
                lifecycleScope.launch {
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
                        if (_binding != null) binding.textViewUserName.text = name
                        dialog.dismiss()
                        Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
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
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.resetPasswordForEmail(
                    email = email,
                    redirectUrl = "com.example.floodwatch://reset-password"
                )
                Toast.makeText(requireContext(), "Reset link sent to $email", Toast.LENGTH_LONG).show()
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
        val label = "Sta. Ana, Taytay, Rizal"
        val mapIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:$HOME_LAT,$HOME_LON?q=${Uri.encode(label)}")
        )
        if (mapIntent.resolveActivity(requireContext().packageManager) != null) startActivity(mapIntent)
        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://www.google.com/maps/search/?api=1&query=$HOME_LAT,$HOME_LON"
        )))
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Log out?")
            .setMessage("Are you sure you want to log out of FloodWatch?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ -> performLogout() }.show()
    }

    private fun performLogout() {
        binding.logoutBtn.isEnabled = false
        binding.logoutBtn.text = "Signing out…"
        lifecycleScope.launch {
            try { SupabaseClient.client.auth.signOut() } catch (_: Exception) { }
            if (!isAdded) return@launch
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("from_logout", true)
            })
            requireActivity().finish()
        }
    }

    private fun chooseProfilePhoto() = profilePhotoPicker.launch(arrayOf("image/*"))

    private fun uploadProfilePhoto(uri: Uri) {
        val resolver = requireContext().contentResolver
        if (!resolver.getType(uri).orEmpty().startsWith("image/")) {
            Toast.makeText(requireContext(), "Please select an image file", Toast.LENGTH_SHORT).show()
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
        profilePreferences().edit().putString(PROFILE_PHOTO_URI, uri.toString()).apply()
        displayProfilePhoto(uri)

        val user = SupabaseClient.client.auth.currentUserOrNull()
        if (user == null) {
            Toast.makeText(requireContext(), "Please sign in again", Toast.LENGTH_SHORT).show()
            return
        }
        setPhotoLoading(true)
        lifecycleScope.launch {
            try {
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Unable to read selected image")
                val path = "${user.id}/avatar.jpg"
                val bucket = SupabaseClient.client.storage.from(PROFILE_BUCKET)
                bucket.upload(path, bytes) { upsert = true }
                val avatarUrl = bucket.publicUrl(path) + "?v=${System.currentTimeMillis()}"
                SupabaseClient.client.auth.updateUser { data { put("avatar_url", avatarUrl) } }
                if (_binding != null) {
                    displayProfilePhoto(avatarUrl)
                    Toast.makeText(requireContext(), "Profile photo updated", Toast.LENGTH_SHORT).show()
                }
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
        setSingleLine(true)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun profilePreferences() = requireContext().getSharedPreferences(PROFILE_PREFS, 0)

    private fun openAppSettings() = startActivity(Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${requireContext().packageName}")
    ))

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val PROFILE_PREFS = "profile_preferences"
        private const val PROFILE_PHOTO_URI = "profile_photo_uri"
        private const val PROFILE_BUCKET = "profile-images"
        private const val PREF_FLOOD_ALERTS = "notify_flood_alerts"
        private const val PREF_WATER_LEVEL = "notify_water_level"
        private const val PREF_COMMUNITY_NEWS = "notify_community_news"
        private const val PREF_EMERGENCY_NAME = "emergency_contact_name"
        private const val PREF_EMERGENCY_PHONE = "emergency_contact_phone"
        private const val DEFAULT_EMERGENCY_NAME = "Marlon Gurion (Friend)"
        private const val MAX_PROFILE_PHOTO_BYTES = 5L * 1024L * 1024L
        private const val HOME_LAT = 14.5374
        private const val HOME_LON = 121.1099
    }
}
