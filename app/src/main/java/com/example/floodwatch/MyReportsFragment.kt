package com.example.floodwatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class ReportStatusFilter(val patterns: List<String>) {
    ALL(emptyList()),
    PENDING(listOf("pending")),
    VERIFIED(listOf("verified")),
    DISMISSED(listOf("dismissed")),
    NEEDS_ATTENTION(listOf("invalid_image", "invalid_information")),
    WITHDRAWN(listOf("withdrawn"));

    fun emptyMessage(): String = when (this) {
        ALL -> "You have not submitted any reports yet."
        VERIFIED -> "You do not have any verified reports yet."
        PENDING -> "You do not have any pending reports."
        DISMISSED -> "You do not have any dismissed reports."
        NEEDS_ATTENTION -> "You do not have reports that need attention."
        WITHDRAWN -> "You do not have any withdrawn reports."
    }
}

class MyReportsFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var emptyView: TextView? = null
    private var loadingView: ProgressBar? = null
    private var loadMoreButton: MaterialButton? = null
    private var refreshButton: MaterialButton? = null
    private val loadedReports = mutableListOf<FloodReport>()
    private var activeFilter = ReportStatusFilter.ALL
    private var nextOffset = 0L
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_my_reports, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.myReportsRecyclerView)
        emptyView = view.findViewById(R.id.myReportsEmpty)
        loadingView = view.findViewById(R.id.myReportsLoading)
        loadMoreButton = view.findViewById(R.id.myReportsLoadMore)
        refreshButton = view.findViewById(R.id.myReportsRefresh)
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())

        val verifiedOnly = arguments?.getBoolean(ARG_VERIFIED_ONLY, false) == true
        activeFilter = if (verifiedOnly) ReportStatusFilter.VERIFIED else ReportStatusFilter.ALL
        view.findViewById<TextView>(R.id.myReportsTitle).text =
            if (verifiedOnly) "Verified Reports" else "My Reports"
        view.findViewById<TextView>(R.id.myReportsSubtitle).text =
            if (verifiedOnly) "Your verified flood report submissions"
            else "Track the status of your flood reports"

        val filters = view.findViewById<ChipGroup>(R.id.myReportsFilters)
        filters.check(if (verifiedOnly) R.id.filterVerified else R.id.filterAll)
        filters.setOnCheckedStateChangeListener { _, checkedIds ->
            activeFilter = when (checkedIds.firstOrNull()) {
                R.id.filterPending -> ReportStatusFilter.PENDING
                R.id.filterVerified -> ReportStatusFilter.VERIFIED
                R.id.filterDismissed -> ReportStatusFilter.DISMISSED
                R.id.filterIssues -> ReportStatusFilter.NEEDS_ATTENTION
                R.id.filterWithdrawn -> ReportStatusFilter.WITHDRAWN
                else -> ReportStatusFilter.ALL
            }
            loadMyReports(reset = true)
        }

        refreshButton?.setOnClickListener { loadMyReports(reset = true) }
        loadMoreButton?.setOnClickListener { loadMyReports(reset = false) }
        loadMyReports(reset = true)
    }

    private fun loadMyReports(reset: Boolean) {
        if (isLoading) return
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
            ?: SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
        if (userId.isNullOrBlank()) {
            showEmpty("Please sign in again to view your reports")
            return
        }

        if (reset) {
            nextOffset = 0L
            loadedReports.clear()
            recyclerView?.adapter = null
        }
        isLoading = true
        loadingView?.visibility = View.VISIBLE
        refreshButton?.isEnabled = false
        loadMoreButton?.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val page = SupabaseClient.client
                    .from("flood_reports")
                    .select {
                        filter {
                            eq("user_id", userId)
                            when (activeFilter.patterns.size) {
                                1 -> ilike("status", activeFilter.patterns.first())
                                in 2..Int.MAX_VALUE ->
                                    ilikeAny("status", activeFilter.patterns)
                            }
                        }
                        order("created_at", Order.DESCENDING)
                        range(nextOffset, nextOffset + PAGE_SIZE - 1)
                    }
                    .decodeList<FloodReport>()

                loadedReports.addAll(page)
                nextOffset += page.size
                if (loadedReports.isEmpty()) {
                    showEmpty(activeFilter.emptyMessage())
                } else {
                    emptyView?.visibility = View.GONE
                    recyclerView?.visibility = View.VISIBLE
                    recyclerView?.adapter = FloodReportAdapter(
                        reports = loadedReports.toList(),
                        onWithdraw = ::confirmWithdraw
                    )
                }
                loadMoreButton?.visibility =
                    if (page.size == PAGE_SIZE.toInt()) View.VISIBLE else View.GONE
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                if (loadedReports.isEmpty()) {
                    showEmpty("Unable to load your reports. Please try again.")
                }
                Toast.makeText(requireContext(), "Reports could not be loaded", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                loadingView?.visibility = View.GONE
                refreshButton?.isEnabled = true
                loadMoreButton?.isEnabled = true
            }
        }
    }

    private fun showEmpty(message: String) {
        emptyView?.text = message
        emptyView?.visibility = View.VISIBLE
        recyclerView?.visibility = View.GONE
        loadMoreButton?.visibility = View.GONE
    }

    private fun confirmWithdraw(report: FloodReport) {
        val reportId = report.id ?: return
        if (!report.status.equals("PENDING", ignoreCase = true)) {
            Toast.makeText(requireContext(), "Only pending reports can be withdrawn.", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Withdraw report?")
            .setMessage(
                "This report will no longer be reviewed by the admin. " +
                    "It will remain in your history and cannot be edited or withdrawn again."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Withdraw") { _, _ -> withdrawReport(reportId) }
            .show()
    }

    private fun withdrawReport(reportId: String) {
        if (isLoading) return
        isLoading = true
        loadingView?.visibility = View.VISIBLE
        refreshButton?.isEnabled = false
        loadMoreButton?.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = SupabaseClient.client.postgrest.rpc(
                    function = "withdraw_own_pending_report",
                    parameters = buildJsonObject { put("p_report_id", reportId) }
                ).decodeAs<Boolean>()
                if (!result) {
                    Toast.makeText(
                        requireContext(),
                        "This report is no longer pending and cannot be withdrawn.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(requireContext(), "Report withdrawn.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Unable to withdraw the report. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isLoading = false
                loadingView?.visibility = View.GONE
                refreshButton?.isEnabled = true
                loadMoreButton?.isEnabled = true
                if (view != null && isAdded) loadMyReports(reset = true)
            }
        }
    }

    override fun onDestroyView() {
        recyclerView = null
        emptyView = null
        loadingView = null
        loadMoreButton = null
        refreshButton = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_VERIFIED_ONLY = "verified_only"
        private const val PAGE_SIZE = 20L

        fun newInstance(verifiedOnly: Boolean = false) = MyReportsFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_VERIFIED_ONLY, verifiedOnly) }
        }
    }
}
