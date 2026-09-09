package com.example.floodwatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ProfileFragment : Fragment() {

    private var profileImage: ImageView? = null

    private val profilePhotoPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            uploadProfilePhoto(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Views
        val editProfile = view.findViewById<LinearLayout>(R.id.editProfile)
        val changePassword = view.findViewById<LinearLayout>(R.id.changePassword)
        val logoutBtn = view.findViewById<MaterialButton>(R.id.logoutBtn)
        val textViewUserEmail = view.findViewById<TextView>(R.id.textViewUserEmail)
        val textViewUserName = view.findViewById<TextView>(R.id.textViewUserName)
        profileImage = view.findViewById(R.id.profileImage)
        val profileEditButton = view.findViewById<ImageView>(R.id.profileEditButton)

        profileImage?.setOnClickListener { chooseProfilePhoto() }
        profileEditButton.setOnClickListener { chooseProfilePhoto() }

        // Fetch and display real user data from Supabase
        val user = SupabaseClient.client.auth.currentUserOrNull()
        if (user != null) {
            textViewUserEmail.text = user.email
            val metadataName = user.userMetadata
                ?.get("full_name")
                ?.jsonPrimitive
                ?.contentOrNull
            textViewUserName.text = metadataName ?: "FloodWatch User"
            val remoteAvatar = user.userMetadata
                ?.get("avatar_url")
                ?.jsonPrimitive
                ?.contentOrNull
            if (!remoteAvatar.isNullOrBlank()) displayProfilePhoto(remoteAvatar)
            else loadSavedProfilePhoto()

            // Signup saves the user's entered name in public.user_profiles.
            lifecycleScope.launch {
                try {
                    val profile = SupabaseClient.client
                        .from("user_profiles")
                        .select {
                            filter { eq("id", user.id) }
                        }
                        .decodeList<UserProfile>()
                        .firstOrNull()
                    profile?.fullName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { textViewUserName.text = it }
                } catch (error: Exception) {
                    // Keep Auth metadata/default name if the profile row is unavailable.
                }
            }
        }

        editProfile?.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Edit Profile feature coming soon!",
                Toast.LENGTH_SHORT
            ).show()
        }

        changePassword?.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Password reset link will be sent to your email.",
                Toast.LENGTH_SHORT
            ).show()
        }

        logoutBtn?.setOnClickListener {
            logoutBtn.isEnabled = false
            logoutBtn.text = "Signing out…"

            lifecycleScope.launch {
                try {
                    SupabaseClient.client.auth.signOut()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("from_logout", true)   // ✅ DAGDAG ITO
                    }
                    startActivity(intent)
                    requireActivity().finish()
                }
            }
        }
    }

    private fun chooseProfilePhoto() {
        profilePhotoPicker.launch(arrayOf("image/*"))
    }

    private fun uploadProfilePhoto(uri: Uri) {
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            requireContext().getSharedPreferences(PROFILE_PREFS, 0)
                .edit()
                .putString(PROFILE_PHOTO_URI, uri.toString())
                .apply()
            displayProfilePhoto(uri)

            val user = SupabaseClient.client.auth.currentUserOrNull()
            if (user == null) {
                Toast.makeText(requireContext(), "Please sign in again", Toast.LENGTH_SHORT).show()
                return
            }

            Toast.makeText(requireContext(), "Uploading profile photo…", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                try {
                    val bytes = requireContext().contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: throw IllegalStateException("Unable to read selected image")

                    val path = "${user.id}/avatar.jpg"
                    val bucket = SupabaseClient.client.storage.from(PROFILE_BUCKET)
                    bucket.upload(path, bytes) {
                        upsert = true
                    }
                    val avatarUrl = bucket.publicUrl(path) + "?v=${System.currentTimeMillis()}"

                    SupabaseClient.client.auth.updateUser {
                        data {
                            put("avatar_url", avatarUrl)
                        }
                    }

                    if (isAdded) {
                        displayProfilePhoto(avatarUrl)
                        Toast.makeText(requireContext(), "Profile photo updated", Toast.LENGTH_SHORT).show()
                    }
                } catch (error: Exception) {
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            "Upload failed: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } catch (error: Exception) {
            Toast.makeText(requireContext(), "Could not save profile photo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedProfilePhoto() {
        val savedUri = requireContext().getSharedPreferences(PROFILE_PREFS, 0)
            .getString(PROFILE_PHOTO_URI, null)
            ?.let(Uri::parse)
        if (savedUri != null) displayProfilePhoto(savedUri)
    }

    private fun displayProfilePhoto(source: Any) {
        profileImage?.let { image ->
            Glide.with(this)
                .load(source)
                .circleCrop()
                .placeholder(R.drawable.ic_boy)
                .error(R.drawable.ic_boy)
                .into(image)
        }
    }

    override fun onDestroyView() {
        profileImage = null
        super.onDestroyView()
    }

    companion object {
        private const val PROFILE_PREFS = "profile_preferences"
        private const val PROFILE_PHOTO_URI = "profile_photo_uri"
        private const val PROFILE_BUCKET = "profile-images"
    }
}
