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

        if (savedInstanceState == null) {
            val session = SupabaseClient.client.auth.currentSessionOrNull()
            if (session == null) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
        }

        fixBottomNavIconTextGap()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { v, _ ->
            v.setPadding(0, 0, 0, 0)
            WindowInsetsCompat.CONSUMED
        }

        val alertsBadge = binding.bottomNavigation.getOrCreateBadge(R.id.navigation_alerts)
        alertsBadge.isVisible = true
        alertsBadge.backgroundColor = Color.RED

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (binding.bottomNavigation.selectedItemId == item.itemId) {
                return@setOnItemSelectedListener false
            }

            val selectedFragment: Fragment = when (item.itemId) {
                R.id.navigation_home         -> HomeFragment()
                R.id.navigation_report       -> ReportFragment()
                R.id.navigation_alerts       -> {
                    alertsBadge.isVisible = false
                    AlertsFragment()
                }
                R.id.navigation_profile -> ProfileFragment()
                else                         -> HomeFragment()
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

    private fun fixBottomNavIconTextGap() {
        binding.bottomNavigation.post {
            try {
                val menuView = binding.bottomNavigation.getChildAt(0) as? android.view.ViewGroup
                    ?: return@post
                val density = resources.displayMetrics.density

                // ✅ I-disable ang clipping para hindi ma-clip ang labels
                binding.bottomNavigation.clipChildren = false
                binding.bottomNavigation.clipToPadding = false
                menuView.clipChildren = false
                menuView.clipToPadding = false

                for (i in 0 until menuView.childCount) {
                    val item = menuView.getChildAt(i) as? android.view.ViewGroup ?: continue
                    item.clipChildren = false
                    item.clipToPadding = false

                    val largeLabel = item.findViewById<android.widget.TextView>(
                        com.google.android.material.R.id.navigation_bar_item_large_label_view
                    )
                    val smallLabel = item.findViewById<android.widget.TextView>(
                        com.google.android.material.R.id.navigation_bar_item_small_label_view
                    )

                    listOf(largeLabel, smallLabel).forEach { label ->
                        label?.translationY = -18f * density
                        label?.setPadding(0, 0, 0, 0)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
