package com.example.floodwatch.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.floodwatch.data.FloodReport
import com.example.floodwatch.data.FloodRepository
import com.example.floodwatch.data.SupabaseClient
import com.example.floodwatch.util.UiError
import com.example.floodwatch.util.toUiError
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class MyReportsUiState(
    val reports: List<FloodReport> = emptyList(),
    val filter: ReportStatusFilter = ReportStatusFilter.ALL,
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val error: UiError? = null,
    val eventId: Long = 0,
    val event: ReportEvent? = null
)

enum class ReportEvent { WITHDRAWN, WITHDRAW_REJECTED }

class MyReportsViewModel : ViewModel() {
    private val _state = MutableLiveData(MyReportsUiState())
    val state: LiveData<MyReportsUiState> = _state
    private var nextOffset = 0L

    fun initialize(filter: ReportStatusFilter) {
        if (_state.value?.reports?.isNotEmpty() == true || _state.value?.loading == true) return
        setFilter(filter)
    }

    fun setFilter(filter: ReportStatusFilter) {
        if (_state.value?.filter == filter && _state.value?.reports?.isNotEmpty() == true) return
        _state.value = MyReportsUiState(filter = filter)
        load(reset = true)
    }

    fun refresh() = load(reset = true)
    fun loadMore() = load(reset = false)

    private fun load(reset: Boolean) {
        val oldState = _state.value ?: MyReportsUiState()
        if (oldState.loading) return
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
            ?: SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
        if (userId.isNullOrBlank()) {
            _state.value = oldState.copy(error = UiError.UNAUTHORIZED)
            return
        }
        if (reset) nextOffset = 0
        viewModelScope.launch {
            _state.value = oldState.copy(
                reports = if (reset) emptyList() else oldState.reports,
                loading = true,
                error = null,
                event = null
            )
            try {
                val page = FloodRepository.userReports(
                    userId = userId,
                    statusPatterns = oldState.filter.patterns,
                    offset = nextOffset,
                    pageSize = PAGE_SIZE
                )
                nextOffset += page.size
                val reports = if (reset) page else oldState.reports + page
                _state.value = oldState.copy(
                    reports = reports,
                    loading = false,
                    hasMore = page.size == PAGE_SIZE.toInt(),
                    error = null,
                    event = null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = oldState.copy(loading = false, error = error.toUiError())
            }
        }
    }

    fun withdraw(reportId: String) {
        val oldState = _state.value ?: return
        if (oldState.loading) return
        viewModelScope.launch {
            _state.value = oldState.copy(loading = true, error = null, event = null)
            try {
                val withdrawn = FloodRepository.withdrawPendingReport(reportId)
                val event = if (withdrawn) ReportEvent.WITHDRAWN else ReportEvent.WITHDRAW_REJECTED
                _state.value = oldState.copy(
                    loading = false,
                    eventId = oldState.eventId + 1,
                    event = event
                )
                if (withdrawn) load(reset = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = oldState.copy(loading = false, error = error.toUiError())
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 20L
    }
}
