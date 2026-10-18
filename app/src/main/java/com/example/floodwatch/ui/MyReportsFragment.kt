package com.example.floodwatch.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.floodwatch.R
import com.example.floodwatch.data.FloodReport
import com.example.floodwatch.util.UiError
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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

    fun emptyMessageRes(): Int = when (this) {
        ALL -> R.string.my_reports_empty_all
        VERIFIED -> R.string.my_reports_empty_verified
        PENDING -> R.string.my_reports_empty_pending
        DISMISSED -> R.string.my_reports_empty_dismissed
        NEEDS_ATTENTION -> R.string.my_reports_empty_attention
        WITHDRAWN -> R.string.my_reports_empty_withdrawn
    }
}

class MyReportsFragment : Fragment() {
    private var recyclerView: RecyclerView? = null
    private var emptyView: TextView? = null
    private var loadingView: ProgressBar? = null
    private var loadMoreButton: MaterialButton? = null
    private var refreshButton: MaterialButton? = null
    private lateinit var adapter: FloodReportAdapter
    private lateinit var viewModel: MyReportsViewModel
    private var lastShownError: UiError? = null
    private var lastEventId = 0L

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

        adapter = FloodReportAdapter(onWithdraw = ::confirmWithdraw)
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        recyclerView?.adapter = adapter
        recyclerView?.setHasFixedSize(true)

        val verifiedOnly = arguments?.getBoolean(ARG_VERIFIED_ONLY, false) == true
        view.findViewById<TextView>(R.id.myReportsTitle).setText(
            if (verifiedOnly) R.string.my_reports_verified_title else R.string.fragment_my_reports_text_1
        )
        view.findViewById<TextView>(R.id.myReportsSubtitle).setText(
            if (verifiedOnly) R.string.my_reports_verified_subtitle else R.string.fragment_my_reports_text_2
        )

        viewModel = ViewModelProvider(this)[MyReportsViewModel::class.java]
        val filters = view.findViewById<ChipGroup>(R.id.myReportsFilters)
        filters.check(if (verifiedOnly) R.id.filterVerified else R.id.filterAll)
        filters.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.setFilter(filterForId(checkedIds.firstOrNull()))
        }
        refreshButton?.setOnClickListener { viewModel.refresh() }
        loadMoreButton?.setOnClickListener { viewModel.loadMore() }
        viewModel.state.observe(viewLifecycleOwner, ::renderState)
        viewModel.initialize(if (verifiedOnly) ReportStatusFilter.VERIFIED else ReportStatusFilter.ALL)
    }

    private fun filterForId(id: Int?): ReportStatusFilter = when (id) {
        R.id.filterPending -> ReportStatusFilter.PENDING
        R.id.filterVerified -> ReportStatusFilter.VERIFIED
        R.id.filterDismissed -> ReportStatusFilter.DISMISSED
        R.id.filterIssues -> ReportStatusFilter.NEEDS_ATTENTION
        R.id.filterWithdrawn -> ReportStatusFilter.WITHDRAWN
        else -> ReportStatusFilter.ALL
    }

    private fun renderState(state: MyReportsUiState) {
        loadingView?.visibility = if (state.loading) View.VISIBLE else View.GONE
        refreshButton?.isEnabled = !state.loading
        loadMoreButton?.isEnabled = !state.loading
        loadMoreButton?.visibility = if (!state.loading && state.hasMore) View.VISIBLE else View.GONE
        adapter.submitList(state.reports)

        val isEmpty = !state.loading && state.reports.isEmpty()
        emptyView?.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView?.visibility = if (state.reports.isEmpty()) View.GONE else View.VISIBLE
        if (isEmpty) {
            emptyView?.setText(state.error?.let(::errorMessage) ?: state.filter.emptyMessageRes())
        }

        if (state.error != null && state.error != lastShownError) {
            Toast.makeText(requireContext(), errorMessage(state.error), Toast.LENGTH_LONG).show()
        }
        lastShownError = state.error

        if (state.event != null && state.eventId > lastEventId) {
            val message = when (state.event) {
                ReportEvent.WITHDRAWN -> R.string.report_withdrawn
                ReportEvent.WITHDRAW_REJECTED -> R.string.report_withdraw_rejected
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            lastEventId = state.eventId
        }
    }

    private fun errorMessage(error: UiError): Int = when (error) {
        UiError.OFFLINE -> R.string.error_offline
        UiError.TIMEOUT -> R.string.error_timeout
        UiError.UNAUTHORIZED -> R.string.error_session_expired
        UiError.SERVER -> R.string.error_server
        UiError.UNKNOWN -> R.string.error_generic_retry
    }

    private fun confirmWithdraw(report: FloodReport) {
        val reportId = report.id ?: return
        if (!report.status.equals("PENDING", ignoreCase = true)) {
            Toast.makeText(requireContext(), R.string.only_pending_withdraw, Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.withdraw_report_title)
            .setMessage(R.string.withdraw_report_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.withdraw) { _, _ -> viewModel.withdraw(reportId) }
            .show()
    }

    override fun onDestroyView() {
        recyclerView?.adapter = null
        recyclerView = null
        emptyView = null
        loadingView = null
        loadMoreButton = null
        refreshButton = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_VERIFIED_ONLY = "verified_only"

        fun newInstance(verifiedOnly: Boolean = false) = MyReportsFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_VERIFIED_ONLY, verifiedOnly) }
        }
    }
}
