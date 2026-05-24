package com.example.floodwatch

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class AlertsFragment : Fragment() {

    private lateinit var recyclerViewReports: RecyclerView
    private lateinit var textViewEmptyReports: TextView
    private lateinit var textViewEmptyAlerts: TextView
    private lateinit var layoutAlerts: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_alerts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerViewReports = view.findViewById(R.id.recyclerViewReports)
        textViewEmptyReports = view.findViewById(R.id.textViewEmptyReports)
        textViewEmptyAlerts = view.findViewById(R.id.textViewEmptyAlerts)
        layoutAlerts = view.findViewById(R.id.layoutAlerts)

        recyclerViewReports.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewReports.isNestedScrollingEnabled = false

        loadFloodAlerts()
        loadVerifiedReports()
    }

    // ── FLOOD ALERTS (admin-posted) ──────────────────────────────
    private fun loadFloodAlerts() {
        lifecycleScope.launch {
            try {
                val alerts = SupabaseClient.client
                    .from("flood_alerts")
                    .select {
                        filter {
                            eq("is_active", true)
                        }
                    }
                    .decodeList<FloodAlert>()

                if (alerts.isEmpty()) {
                    textViewEmptyAlerts.visibility = View.VISIBLE
                    layoutAlerts.visibility = View.GONE
                } else {
                    textViewEmptyAlerts.visibility = View.GONE
                    layoutAlerts.visibility = View.VISIBLE
                    layoutAlerts.removeAllViews()
                    alerts.forEach { alert -> addAlertCard(alert) }
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading alerts: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addAlertCard(alert: FloodAlert) {
        val card = CardView(requireContext()).apply {
            radius = 16f
            cardElevation = 4f
            setCardBackgroundColor(Color.WHITE)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
            layoutParams = params
        }

        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
        }

        // Severity badge color
        val badgeColor = when (alert.severity) {
            AlertSeverity.ADVISORY -> "#10B981"
            AlertSeverity.WATCH    -> "#F59E0B"
            AlertSeverity.WARNING  -> "#EF4444"
            AlertSeverity.CRITICAL -> "#7C3AED"
        }

        val severityView = TextView(requireContext()).apply {
            text = "⚠ ${alert.severity}"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(badgeColor))
            setPadding(20, 6, 20, 6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
        }

        val titleView = TextView(requireContext()).apply {
            text = alert.title
            textSize = 15f
            setTextColor(Color.parseColor("#0F2040"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 }
        }

        val messageView = TextView(requireContext()).apply {
            text = alert.message
            textSize = 13f
            setTextColor(Color.parseColor("#5A6268"))
            setLineSpacing(0f, 1.4f)  // ← palitan ito
        }

        inner.addView(severityView)
        inner.addView(titleView)
        inner.addView(messageView)
        card.addView(inner)
        layoutAlerts.addView(card)
    }

    // ── VERIFIED REPORTS (user-submitted, admin-verified) ────────
    private fun loadVerifiedReports() {
        lifecycleScope.launch {
            try {
                val reports = SupabaseClient.client
                    .from("flood_reports")
                    .select {
                        filter {
                            eq("status", "VERIFIED")
                        }
                    }
                    .decodeList<FloodReport>()

                if (reports.isEmpty()) {
                    textViewEmptyReports.visibility = View.VISIBLE
                    recyclerViewReports.visibility = View.GONE
                } else {
                    textViewEmptyReports.visibility = View.GONE
                    recyclerViewReports.visibility = View.VISIBLE
                    recyclerViewReports.adapter = FloodReportAdapter(reports)
                }

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error loading reports: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}