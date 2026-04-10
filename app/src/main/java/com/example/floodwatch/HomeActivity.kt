package com.example.floodwatch

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.floodwatch.databinding.ActivityHomeBinding
import io.github.jan.supabase.auth.auth

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge insets setup
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Red dot badge on Alerts tab
        val alertsBadge = binding.bottomNavigation.getOrCreateBadge(R.id.navigation_alerts)
        alertsBadge.isVisible = true
        alertsBadge.backgroundColor = Color.RED

        // Bottom Navigation logic
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (binding.bottomNavigation.selectedItemId == item.itemId) {
                return@setOnItemSelectedListener false
            }

            val selectedFragment: Fragment = when (item.itemId) {
                R.id.navigation_home    -> HomeFragment()
                R.id.navigation_report  -> ReportFragment()
                R.id.navigation_alerts  -> {
                    alertsBadge.isVisible = false
                    AlertsFragment()
                }
                R.id.navigation_profile -> ProfileFragment()
                else                    -> HomeFragment()
            }

            supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, selectedFragment)
                .commit()

            true
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
            binding.bottomNavigation.selectedItemId = R.id.navigation_home
        }
    }

    override fun onStart() {
        super.onStart()
        val session = SupabaseClient.client.auth.currentSessionOrNull()
        if (session == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
