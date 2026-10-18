package com.example.floodwatch.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.floodwatch.R
import com.example.floodwatch.data.AlertSeverity
import com.example.floodwatch.data.FloodAlert
import com.example.floodwatch.util.UiError
import com.google.android.material.button.MaterialButton

class AlertsFragment : Fragment() {
    private lateinit var recyclerViewReports: RecyclerView
    private lateinit var textViewEmptyReports: TextView
    private lateinit var textViewEmptyAlerts: TextView
    private lateinit var layoutAlerts: LinearLayout
    private lateinit var loadingView: ProgressBar
    private lateinit var retryButton: MaterialButton
    private lateinit var reportAdapter: FloodReportAdapter
    private lateinit var viewModel: AlertsViewModel
    private var lastShownError: UiError? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_alerts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerViewReports = view.findViewById(R.id.recyclerViewReports)
        textViewEmptyReports = view.findViewById(R.id.textViewEmptyReports)
        textViewEmptyAlerts = view.findViewById(R.id.textViewEmptyAlerts)
        layoutAlerts = view.findViewById(R.id.layoutAlerts)
        loadingView = view.findViewById(R.id.alertsLoading)
        retryButton = view.findViewById(R.id.alertsRetry)

        reportAdapter = FloodReportAdapter()
        recyclerViewReports.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewReports.isNestedScrollingEnabled = false
        recyclerViewReports.adapter = reportAdapter

        viewModel = ViewModelProvider(this)[AlertsViewModel::class.java]
        retryButton.setOnClickListener { viewModel.refresh(forceRefresh = true) }
        viewModel.state.observe(viewLifecycleOwner, ::renderState)
    }

    private fun renderState(state: AlertsUiState) {
        loadingView.visibility = if (state.loading) View.VISIBLE else View.GONE
        retryButton.visibility = if (state.error != null) View.VISIBLE else View.GONE

        textViewEmptyAlerts.visibility =
            if (!state.loading && state.alerts.isEmpty()) View.VISIBLE else View.GONE
        layoutAlerts.visibility = if (state.alerts.isEmpty()) View.GONE else View.VISIBLE
        layoutAlerts.removeAllViews()
        state.alerts.forEach(::addAlertCard)

        textViewEmptyReports.visibility =
            if (!state.loading && state.reports.isEmpty()) View.VISIBLE else View.GONE
        recyclerViewReports.visibility = if (state.reports.isEmpty()) View.GONE else View.VISIBLE
        reportAdapter.submitList(state.reports)

        if (state.error != null && state.error != lastShownError) {
            Toast.makeText(requireContext(), errorMessage(state.error), Toast.LENGTH_LONG).show()
        }
        lastShownError = state.error
    }

    private fun errorMessage(error: UiError): Int = when (error) {
        UiError.OFFLINE -> R.string.error_offline
        UiError.TIMEOUT -> R.string.error_timeout
        UiError.UNAUTHORIZED -> R.string.error_session_expired
        UiError.SERVER -> R.string.error_server
        UiError.UNKNOWN -> R.string.error_generic_retry
    }

    private fun addAlertCard(alert: FloodAlert) {
        val card = CardView(requireContext()).apply {
            radius = 16f
            cardElevation = 4f
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }
        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
        }
        val badgeColor = when (alert.severity) {
            AlertSeverity.ADVISORY -> "#10B981"
            AlertSeverity.WATCH -> "#F59E0B"
            AlertSeverity.WARNING -> "#EF4444"
            AlertSeverity.CRITICAL -> "#7C3AED"
        }
        val severityView = TextView(requireContext()).apply {
            text = getString(R.string.alert_severity_format, alert.severity)
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(badgeColor.toColorInt())
            setPadding(20, 6, 20, 6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
        }
        val titleView = TextView(requireContext()).apply {
            text = alert.title
            textSize = 15f
            setTextColor("#0F2040".toColorInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 }
        }
        val messageView = TextView(requireContext()).apply {
            text = alert.message
            textSize = 13f
            setTextColor("#5A6268".toColorInt())
            setLineSpacing(0f, 1.4f)
        }
        inner.addView(severityView)
        inner.addView(titleView)
        inner.addView(messageView)
        card.addView(inner)
        layoutAlerts.addView(card)
    }
}
