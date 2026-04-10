package com.example.floodwatch

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

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

        // Fetch and display real user data from Supabase
        val user = SupabaseClient.client.auth.currentUserOrNull()
        if (user != null) {
            textViewUserEmail.text = user.email
            // Default name if metadata isn't set yet
            textViewUserName.text = user.userMetadata?.get("full_name")?.toString() ?: "FloodWatch User"
        }

        editProfile?.setOnClickListener {
            Toast.makeText(requireContext(), "Edit Profile feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        changePassword?.setOnClickListener {
            Toast.makeText(requireContext(), "Password reset link will be sent to your email.", Toast.LENGTH_SHORT).show()
        }

        logoutBtn?.setOnClickListener {
            lifecycleScope.launch {
                try {
                    SupabaseClient.client.auth.signOut()
                    Toast.makeText(requireContext(), "Logged Out", Toast.LENGTH_SHORT).show()
                    
                    // Clear backstack and go to Login
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error logging out: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
