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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class MyReportsFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var emptyView: TextView? = null
    private var loadingView: ProgressBar? = null

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
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        loadMyReports()
    }

    private fun loadMyReports() {
        // The Activity already validated the session. On a cold start the Auth
        // module can restore the session a moment later, so use the session's
        // user id as a reliable fallback instead of incorrectly asking the
        // user to sign in again.
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
            ?: SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
        if (userId.isNullOrBlank()) {
            showEmpty("Please sign in again to view your reports")
            return
        }

        loadingView?.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val reports = SupabaseClient.client
                    .from("flood_reports")
                    .select {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<FloodReport>()
                    .sortedByDescending { it.createdAt.orEmpty() }

                if (reports.isEmpty()) {
                    showEmpty("You have not submitted any reports yet.")
                } else {
                    emptyView?.visibility = View.GONE
                    recyclerView?.visibility = View.VISIBLE
                    recyclerView?.adapter = FloodReportAdapter(reports)
                }
            } catch (error: Exception) {
                showEmpty("Unable to load your reports. Please try again.")
                Toast.makeText(requireContext(), "Reports could not be loaded", Toast.LENGTH_SHORT).show()
            } finally {
                loadingView?.visibility = View.GONE
            }
        }
    }

    private fun showEmpty(message: String) {
        emptyView?.text = message
        emptyView?.visibility = View.VISIBLE
        recyclerView?.visibility = View.GONE
    }

    override fun onDestroyView() {
        recyclerView = null
        emptyView = null
        loadingView = null
        super.onDestroyView()
    }
}
