package com.example.floodwatch

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.floodwatch.databinding.FragmentHomeBinding
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var googleMap: GoogleMap
    private var currentOverlay: TileOverlay? = null
    private val floodReportMarkers = mutableListOf<Marker>()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchGpsAltitude()
        else _binding?.textViewElevation?.text = "GPS permission required"
    }

    private val apiKey: String = BuildConfig.OPENWEATHER_API_KEY
    private var currentLayer = "precipitation_new"
    private var latestRainfallMm = 0.0

    private val serviceArea = "${AppLocation.BARANGAY}, ${AppLocation.MUNICIPALITY}"
    private val serviceAreaLocation = LatLng(AppLocation.LAT, AppLocation.LNG)

    private val weatherApi: OpenWeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenWeatherApi::class.java)
    }

    // [CnS] Requirement: Update every 15 mins to know flood conditions
    private val refreshInterval = 15 * 60 * 1000L
    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (::googleMap.isInitialized && _binding != null) {
                refreshData()
                handler.postDelayed(this, refreshInterval)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)
        setupClickListeners()
        binding.textViewStatus.text = "Monitoring • $serviceArea"
        binding.textViewRiskArea.text = serviceArea
        binding.textViewAlertDetails.paintFlags =
            binding.textViewAlertDetails.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.textViewRiskDetails.paintFlags =
            binding.textViewRiskDetails.paintFlags or Paint.UNDERLINE_TEXT_FLAG
    }

    private fun setupClickListeners() {
        binding.buttonZoomIn.setOnClickListener { if (::googleMap.isInitialized) googleMap.animateCamera(CameraUpdateFactory.zoomIn()) }
        binding.buttonZoomOut.setOnClickListener { if (::googleMap.isInitialized) googleMap.animateCamera(CameraUpdateFactory.zoomOut()) }
        binding.buttonRainLayer.setOnClickListener {
            switchWeatherLayer("precipitation_new")
            if (latestRainfallMm <= 0.0) {
                Toast.makeText(
                    requireContext(),
                    "Rain layer is active. No rainfall is currently detected in this area.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.buttonTempLayer.setOnClickListener { switchWeatherLayer("temp_new") }
        binding.buttonCloudLayer.setOnClickListener { switchWeatherLayer("clouds_new") }
        binding.buttonMyLocation.setOnClickListener { fetchGpsAltitude() }
        binding.textViewAlertDetails.setOnClickListener { showAlertDetails() }
        binding.textViewRiskDetails.setOnClickListener { showRiskDetails() }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        // A custom lower-right location button is used instead of Google's top-right button.
        googleMap.uiSettings.isMyLocationButtonEnabled = false

        // [CnS] Requirement: Include above sea level area (satellite/hybrid for prediction)
        // Keep the detailed satellite imagery requested for the dashboard.
        googleMap.mapType = GoogleMap.MAP_TYPE_HYBRID

        // Let the hybrid base tiles finish first. Loading traffic at the same time
        // competes with the cold satellite download after Clear Data.
        googleMap.isTrafficEnabled = false

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                serviceAreaLocation,
                AppLocation.DEFAULT_ZOOM
            )
        )
        googleMap.setOnMapLoadedCallback {
            // This callback is only needed for the initial cold load.
            googleMap.setOnMapLoadedCallback(null)
            googleMap.isTrafficEnabled = true
        }
        refreshData()
        startAutoRefresh()
    }

    private fun fetchGpsAltitude() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        binding.textViewElevation.text = "Reading GPS elevation..."
        if (::googleMap.isInitialized) {
            // The blue dot shows the exact position represented by the GPS altitude badge.
            try {
                googleMap.isMyLocationEnabled = true
            } catch (error: SecurityException) {
                Log.e("GpsAltitude", "Unable to enable location layer", error)
            }
        }
        val client = LocationServices.getFusedLocationProviderClient(requireActivity())
        val token = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { location ->
                if (location?.hasAltitude() == true) {
                    val accuracy = if (location.hasVerticalAccuracy()) {
                        " ±%.0f m".format(location.verticalAccuracyMeters)
                    } else ""
                    _binding?.textViewElevation?.text =
                        "GPS elevation: %.1f m$accuracy".format(location.altitude)
                    if (::googleMap.isInitialized) {
                        val devicePosition = LatLng(location.latitude, location.longitude)
                        googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(devicePosition, 16f)
                        )
                    }
                } else {
                    _binding?.textViewElevation?.text = "GPS elevation unavailable"
                }
            }
            .addOnFailureListener { error ->
                Log.e("GpsAltitude", "Unable to read altitude: ${error.message}")
                _binding?.textViewElevation?.text = "GPS elevation unavailable"
            }
    }

    private fun refreshData() {
        // Weather tiles load on demand when a layer button is selected. Starting
        // them here makes two tile providers compete during the base map cold load.
        updateLastUpdated()
        fetchFloodReports()
        fetchFloodAlerts()
        fetchWeatherData(AppLocation.LAT, AppLocation.LNG)
        // Force the selected weather layer to request fresh tiles every refresh.
        currentOverlay?.clearTileCache()
    }

    private fun fetchWeatherData(lat: Double, lon: Double) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = weatherApi.getWeather(lat, lon, apiKey)
                _binding?.let { b ->
                    b.textViewTemp.text = "${response.main.temp.toInt()}°C"
                    b.textViewCondition.text = response.weather.firstOrNull()?.main ?: "--"
                    b.textViewHumidity.text = "${response.main.humidity}% Humidity"
                    val windKph = response.wind.speed * METERS_PER_SECOND_TO_KPH
                    b.textViewWindSpeed.text = windKph.toInt().toString()

                    val rainVal = response.rain?.oneHour ?: 0.0
                    latestRainfallMm = rainVal
                    // Keep one decimal place so light rain such as 0.8 mm is not shown as zero.
                    b.textViewRainfall.text = String.format(Locale.getDefault(), "%.1f", rainVal)

                    val temp = response.main.temp
                    val condition = response.weather.firstOrNull()?.main ?: ""

                    when {
                        temp >= 32 -> {
                            b.imageViewWeatherIcon.setImageResource(R.drawable.ic_sun)
                        }
                        condition.contains("Rain", true) -> {
                            b.imageViewWeatherIcon.setImageResource(R.drawable.ic_rain)
                        }
                        condition.contains("Cloud", true) -> {
                            b.imageViewWeatherIcon.setImageResource(R.drawable.ic_cloud)
                        }
                        condition.contains("Clear", true) -> {
                            b.imageViewWeatherIcon.setImageResource(R.drawable.ic_sun)
                        }
                        else -> {
                            b.imageViewWeatherIcon.setImageResource(R.drawable.ic_sun)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WeatherAPI", "Error: ${e.message}")
                _binding?.let { b ->
                    b.textViewCondition.text = "Weather unavailable"
                    b.textViewTemp.text = "--°C"
                    b.textViewHumidity.text = "--% Humidity"
                    b.textViewRainfall.text = "--"
                    b.textViewWindSpeed.text = "--"
                }
            }
        }
    }

    private fun fetchFloodReports() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val reports = SupabaseClient.client.postgrest
                    .from("flood_reports")
                    .select()
                    .decodeList<FloodReport>()

                // Map markers — VERIFIED na reports lang
                val verifiedReports = reports.filter {
                    it.status.equals("VERIFIED", ignoreCase = true)
                }
                val activeCutoff = System.currentTimeMillis() - REPORT_ACTIVE_WINDOW_MS
                val activeVerifiedReports = verifiedReports.filter { report ->
                    report.createdAt?.let(::parseTimestampMillis)?.let { it >= activeCutoff } == true
                }

                // Do not call GoogleMap.clear() here. It also removes the active
                // weather TileOverlay, which made the map layer disappear as soon
                // as the asynchronous report request completed.
                floodReportMarkers.forEach { it.remove() }
                floodReportMarkers.clear()
                activeVerifiedReports.forEach { report ->
                    val timestamp = report.createdAt?.let { formatReportDate(it) } ?: ""
                    val details = "Level: ${report.floodLevel ?: "N/A"} | Passable: ${report.passability ?: "Unknown"}\nReported: $timestamp"

                    googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(report.latitude, report.longitude))
                            .title("✓ Verified Flood Report")
                            .snippet(details)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    )?.let(floodReportMarkers::add)
                }

                updateRiskCard(activeVerifiedReports)

            } catch (e: Exception) {
                Log.e("FloodWatch", "Supabase Error: ${e.message}")
                _binding?.let { b ->
                    b.textViewRiskLevel.text = "Data unavailable"
                    b.textViewStreetStatus.text = "UNKNOWN"
                    b.textViewStreetStatus.setTextColor(Color.GRAY)
                    b.progressBarRisk.progress = 0
                }
            }
        }
    }

    private fun fetchFloodAlerts() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val alerts = SupabaseClient.client.postgrest
                    .from("flood_alerts")
                    .select {
                        filter {
                            eq("is_active", true)
                        }
                    }
                    .decodeList<FloodAlert>()

                _binding?.let { b ->
                    b.textViewActiveAlerts.text = String.format("%02d", alerts.size)
                    updateEvacuationStatus(alerts)

                    if (alerts.isNotEmpty()) {
                        val latest = alerts.maxByOrNull {
                            parseTimestampMillis(it.createdAt) ?: Long.MIN_VALUE
                        } ?: alerts.first()
                        b.textViewAlertTitle.text = latest.title
                        b.textViewAlertDesc.text  = latest.message
                        b.textViewAlertUpdated.text = formatRelativeTime(latest.createdAt)
                    } else {
                        b.textViewAlertTitle.text = "No active alerts"
                        b.textViewAlertDesc.text  = "All clear. No flood incidents reported."
                        b.textViewAlertUpdated.text = ""
                    }
                }
            } catch (e: Exception) {
                Log.e("FloodWatch", "Alerts Error: ${e.message}")
                _binding?.let { b ->
                    b.textViewActiveAlerts.text = "--"
                    b.textViewEvacStatus.text = "UNKNOWN"
                    b.textViewEvacStatus.setTextColor(Color.GRAY)
                    b.textViewAlertTitle.text = "Alerts unavailable"
                    b.textViewAlertDesc.text = "Check your connection and try again."
                    b.textViewAlertUpdated.text = ""
                }
            }
        }
    }

    private fun updateEvacuationStatus(alerts: List<FloodAlert>) {
        val highestSeverity = alerts.maxOfOrNull { it.severity.ordinal } ?: -1
        val (status, color) = when (highestSeverity) {
            AlertSeverity.CRITICAL.ordinal -> "EVACUATE" to "#DC2626"
            AlertSeverity.WARNING.ordinal -> "READY" to "#EA580C"
            AlertSeverity.WATCH.ordinal -> "STANDBY" to "#D97706"
            AlertSeverity.ADVISORY.ordinal -> "MONITOR" to "#0284C7"
            else -> "NORMAL" to "#16A34A"
        }
        _binding?.textViewEvacStatus?.apply {
            text = status
            setTextColor(Color.parseColor(color))
        }
    }

    private fun updateRiskCard(reports: List<FloodReport>) {
        // The newest verified report represents the current road condition.
        // An older, more severe report must not override a newer update.
        val latestReport = reports.maxByOrNull { report ->
            report.createdAt?.let(::parseTimestampMillis) ?: Long.MIN_VALUE
        }
        val latestSeverity = latestReport?.let { report ->
            report.severity ?: when (report.floodLevel?.uppercase()) {
                "CRITICAL" -> 4
                "HIGH" -> 3
                "MODERATE", "MEDIUM" -> 2
                "LOW" -> 1
                else -> 0
            }
        } ?: 0

        val risk = when (latestSeverity.coerceIn(0, 4)) {
            4 -> RiskUi("Critical Threat", "NOT PASSABLE", 100, "#DC2626")
            3 -> RiskUi("High Threat", "AVOID AREA", 75, "#EF4444")
            2 -> RiskUi("Moderate Threat", "CAUTION", 50, "#D97706")
            1 -> RiskUi("Low Threat", "PASSABLE", 25, "#16A34A")
            else -> RiskUi("No Verified Risk", "STABLE", 0, "#16A34A")
        }

        _binding?.let { b ->
            b.textViewRiskLevel.text = risk.label
            b.textViewStreetStatus.text = risk.status
            b.textViewStreetStatus.setTextColor(Color.parseColor(risk.color))
            b.progressBarRisk.progress = risk.progress
            b.progressBarRisk.progressTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor(risk.color))
        }
    }

    private fun formatRelativeTime(dateStr: String?): String {
        if (dateStr == null) return ""
        return try {
            val alertTime = parseTimestampMillis(dateStr) ?: return ""
            val now = System.currentTimeMillis()
            val diffMs = (now - alertTime).coerceAtLeast(0)

            val minutes = diffMs / 60_000
            val hours   = diffMs / 3_600_000
            val days    = diffMs / 86_400_000

            when {
                minutes < 1  -> "Updated just now"
                minutes < 60 -> "Updated ${minutes} min ago"
                hours   < 24 -> "Updated ${hours} hr ago"
                else         -> "Updated ${days} day${if (days > 1) "s" else ""} ago"
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun addWeatherOverlay(layer: String) {
        currentLayer = layer
        currentOverlay?.remove()
        val tileProvider = object : UrlTileProvider(256, 256) {
            override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
                return try { URL("https://tile.openweathermap.org/map/$layer/$zoom/$x/$y.png?appid=$apiKey") }
                catch (e: Exception) { null }
            }
        }
        currentOverlay = googleMap.addTileOverlay(
            TileOverlayOptions()
                .tileProvider(tileProvider)
                .visible(true)
                .fadeIn(true)
                .zIndex(10f)
                .transparency(0.05f)
        )
    }

    private fun switchWeatherLayer(layer: String) {
        addWeatherOverlay(layer)
        updateWeatherLayerButtons(layer)
        updateLastUpdated()
    }

    private fun updateWeatherLayerButtons(layer: String) {
        val inactive = ColorStateList.valueOf(Color.parseColor("#475569"))
        binding.buttonRainLayer.backgroundTintList = inactive
        binding.buttonTempLayer.backgroundTintList = inactive
        binding.buttonCloudLayer.backgroundTintList = inactive

        val selectedButton = when (layer) {
            "precipitation_new" -> binding.buttonRainLayer
            "temp_new" -> binding.buttonTempLayer
            else -> binding.buttonCloudLayer
        }
        val selectedColor = when (layer) {
            "precipitation_new" -> "#2563EB"
            "temp_new" -> "#EF4444"
            else -> "#6B7280"
        }
        selectedButton.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(selectedColor))
    }

    private fun updateLastUpdated() {
        val sdf = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault())
        _binding?.textLastUpdated?.text = "Last updated: ${sdf.format(Date())}"
    }

    private fun formatReportDate(dateStr: String): String {
        val timestamp = parseTimestampMillis(dateStr) ?: return ""
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    private fun parseTimestampMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(value).toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    private fun showAlertDetails() {
        val b = _binding ?: return
        MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_FloodWatch_MaterialAlertDialog
        )
            .setTitle(b.textViewAlertTitle.text)
            .setMessage(b.textViewAlertDesc.text)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showRiskDetails() {
        val b = _binding ?: return
        MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_FloodWatch_MaterialAlertDialog
        )
            .setTitle(b.textViewRiskLevel.text)
            .setMessage(
                "Area status: ${b.textViewStreetStatus.text}\n" +
                    "Risk is based on the latest verified report from the last 24 hours."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun startAutoRefresh() {
        handler.removeCallbacks(refreshRunnable)
        if (::googleMap.isInitialized && _binding != null) {
            handler.postDelayed(refreshRunnable, refreshInterval)
        }
    }

    override fun onStart() {
        super.onStart()
        _binding?.mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (::googleMap.isInitialized) {
            refreshData()
            startAutoRefresh()
        }
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        _binding?.mapView?.onPause()
        super.onPause()
    }

    override fun onStop() {
        _binding?.mapView?.onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(refreshRunnable)
        _binding?.mapView?.onDestroy()
        _binding = null
        super.onDestroyView()
    }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.mapView?.onSaveInstanceState(outState)
    }

    private data class RiskUi(
        val label: String,
        val status: String,
        val progress: Int,
        val color: String
    )

    companion object {
        private const val METERS_PER_SECOND_TO_KPH = 3.6
        private const val REPORT_ACTIVE_WINDOW_MS = 24L * 60L * 60L * 1000L
    }
}
