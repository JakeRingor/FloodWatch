package com.example.floodwatch.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.floodwatch.data.FloodAlert
import com.example.floodwatch.data.FloodReport
import com.example.floodwatch.data.FloodRepository
import com.example.floodwatch.util.UiError
import com.example.floodwatch.util.toUiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class AlertsUiState(
    val loading: Boolean = false,
    val alerts: List<FloodAlert> = emptyList(),
    val reports: List<FloodReport> = emptyList(),
    val error: UiError? = null
)

class AlertsViewModel : ViewModel() {
    private val _state = MutableLiveData(AlertsUiState())
    val state: LiveData<AlertsUiState> = _state

    init {
        refresh()
    }

    fun refresh(forceRefresh: Boolean = false) {
        if (_state.value?.loading == true) return
        viewModelScope.launch {
            _state.value = _state.value.orEmpty().copy(loading = true, error = null)
            try {
                val alerts = async { FloodRepository.activeAlerts(forceRefresh) }
                val reports = async { FloodRepository.verifiedReports(forceRefresh) }
                _state.value = AlertsUiState(
                    alerts = alerts.await(),
                    reports = reports.await()
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.orEmpty().copy(
                    loading = false,
                    error = error.toUiError()
                )
            }
        }
    }

    private fun AlertsUiState?.orEmpty() = this ?: AlertsUiState()
}
