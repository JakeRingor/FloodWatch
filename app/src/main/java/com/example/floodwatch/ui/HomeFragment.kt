package com.example.floodwatch.ui

import com.example.floodwatch.BuildConfig
import com.example.floodwatch.data.AlertSeverity
import com.example.floodwatch.data.AppLocation
import com.example.floodwatch.data.FloodAlert
import com.example.floodwatch.data.FloodReport
import com.example.floodwatch.data.PermanentFloodZone
import com.example.floodwatch.data.OpenWeatherApi
import com.example.floodwatch.data.FloodRepository
import com.example.floodwatch.data.RetrofitInstance
import com.example.floodwatch.data.SupabaseClient
import com.example.floodwatch.R

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
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.example.floodwatch.databinding.FragmentHomeBinding
import com.example.floodwatch.util.ReportTimestampFormatter
import com.example.floodwatch.util.FloodReferenceCandidate
import com.example.floodwatch.util.FloodReferenceSelector
import com.example.floodwatch.util.PermanentFloodZoneResolver
import com.example.floodwatch.util.PermanentZoneDisplayState
import com.bumptech.glide.Glide
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import java.net.URL
import java.text.NumberFormat
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
    private var floodReferenceCircle: Circle? = null
    private var floodReferenceReport: FloodReport? = null
    private val permanentZoneCircles = mutableListOf<Circle>()
    private val permanentZoneByCircleId = mutableMapOf<String, PermanentFloodZone>()
    private var latestPermanentZones: List<PermanentFloodZone> = emptyList()
    private var activeReportsForZones: List<FloodReport> = emptyList()
    private var reportsRealtimeChannel: RealtimeChannel? = null
    private var reportsRealtimeJob: Job? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchGpsAltitude()
        else _binding?.textViewElevation?.setText(R.string.gps_permission_required)
    }

    private val apiKey: String = BuildConfig.OPENWEATHER_API_KEY
    private var currentLayer = "precipitation_new"
    private var latestRainfallMm = 0.0

    private val serviceArea = "${AppLocation.BARANGAY}, ${AppLocation.MUNICIPALITY}"
    private val serviceAreaLocation = LatLng(AppLocation.LAT, AppLocation.LNG)

    private val weatherApi: OpenWeatherApi by lazy { RetrofitInstance.api }

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
        binding.textViewStatus.text = getString(R.string.monitoring_service_area_format, serviceArea)
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
        googleMap.setOnInfoWindowClickListener { marker ->
            (marker.tag as? FloodReport)?.let(::showFloodReportDetails)
        }
        googleMap.setOnCircleClickListener { circle ->
            if (circle.id == floodReferenceCircle?.id) {
                floodReferenceReport?.let(::showFloodReferenceDetails)
            } else {
                permanentZoneByCircleId[circle.id]?.let(::showPermanentZoneDetails)
            }
        }
        observeReportChanges()
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

        binding.textViewElevation.setText(R.string.reading_gps_elevation)
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
                        " ±%.0f m".format(Locale.getDefault(), location.verticalAccuracyMeters)
                    } else ""
                    _binding?.textViewElevation?.text = getString(
                        R.string.gps_elevation_format,
                        location.altitude,
                        accuracy
                    )
                    if (::googleMap.isInitialized) {
                        val devicePosition = LatLng(location.latitude, location.longitude)
                        googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(devicePosition, 16f)
                        )
                    }
                } else {
                    _binding?.textViewElevation?.setText(R.string.gps_elevation_unavailable)
                }
            }
            .addOnFailureListener { error ->
                Log.e("GpsAltitude", "Unable to read altitude: ${error.message}")
                _binding?.textViewElevation?.setText(R.string.gps_elevation_unavailable)
            }
    }

    private fun refreshData() {
        // Weather tiles load on demand when a layer button is selected. Starting
        // them here makes two tile providers compete during the base map cold load.
        updateLastUpdated()
        fetchFloodReports()
        fetchFloodAlerts()
        fetchPermanentFloodZones()
        fetchWeatherData(AppLocation.LAT, AppLocation.LNG)
        // Force the selected weather layer to request fresh tiles every refresh.
        currentOverlay?.clearTileCache()
    }

    private fun fetchWeatherData(lat: Double, lon: Double) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = weatherApi.getWeather(lat, lon, apiKey)
                _binding?.let { b ->
                    b.textViewTemp.text = getString(
                        R.string.temperature_celsius_format,
                        response.main.temp.toInt()
                    )
                    b.textViewCondition.text = response.weather.firstOrNull()?.main ?: "--"
                    b.textViewHumidity.text = getString(
                        R.string.humidity_format,
                        response.main.humidity
                    )
                    val windKph = response.wind.speed * METERS_PER_SECOND_TO_KPH
                    b.textViewWindSpeed.text = NumberFormat.getIntegerInstance().format(windKph.toInt())

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("WeatherAPI", "Error: ${e.message}")
                _binding?.let { b ->
                    b.textViewCondition.setText(R.string.weather_unavailable)
                    b.textViewTemp.setText(R.string.temperature_unavailable)
                    b.textViewHumidity.setText(R.string.humidity_unavailable)
                    b.textViewRainfall.setText(R.string.value_unavailable)
                    b.textViewWindSpeed.setText(R.string.value_unavailable)
                }
            }
        }
    }

    private fun fetchFloodReports(forceRefresh: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val reports = FloodRepository.verifiedReports(forceRefresh)

                // Map markers — VERIFIED na reports lang
                val activeCutoff = System.currentTimeMillis() - REPORT_ACTIVE_WINDOW_MS
                val activeVerifiedReports = reports.filter { report ->
                    report.createdAt?.let(::parseTimestampMillis)?.let { it >= activeCutoff } == true
                }

                // Do not call GoogleMap.clear() here. It also removes the active
                // weather TileOverlay, which made the map layer disappear as soon
                // as the asynchronous report request completed.
                floodReportMarkers.forEach { it.remove() }
                floodReportMarkers.clear()
                activeVerifiedReports.forEach { report ->
                    val timestamp = report.createdAt?.let { formatReportDate(it) } ?: ""
                    val details = getString(
                        R.string.map_report_marker_preview,
                        report.floodLevel ?: getString(R.string.value_not_available),
                        report.passability ?: getString(R.string.status_unknown),
                        timestamp
                    )

                    val marker = googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(report.latitude, report.longitude))
                            .title(getString(R.string.verified_flood_report))
                            .snippet(details)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    )
                    marker?.tag = report
                    marker?.let(floodReportMarkers::add)
                }
                activeReportsForZones = activeVerifiedReports

                renderFloodReference(activeVerifiedReports)
                renderPermanentFloodZones()

                updateRiskCard(activeVerifiedReports)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FloodWatch", "Supabase Error: ${e.message}")
                _binding?.let { b ->
                    b.textViewRiskLevel.setText(R.string.data_unavailable)
                    b.textViewStreetStatus.setText(R.string.status_unknown)
                    b.textViewStreetStatus.setTextColor(Color.GRAY)
                    b.progressBarRisk.progress = 0
                }
            }
        }
    }

    private fun fetchFloodAlerts() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val alerts = FloodRepository.activeAlerts()

                _binding?.let { b ->
                    b.textViewActiveAlerts.text = String.format(
                        Locale.getDefault(),
                        "%02d",
                        alerts.size
                    )
                    updateEvacuationStatus(alerts)

                    if (alerts.isNotEmpty()) {
                        val latest = alerts.maxByOrNull {
                            parseTimestampMillis(it.createdAt) ?: Long.MIN_VALUE
                        } ?: alerts.first()
                        b.textViewAlertTitle.text = latest.title
                        b.textViewAlertDesc.text  = latest.message
                        b.textViewAlertUpdated.text = formatRelativeTime(latest.createdAt)
                    } else {
                        b.textViewAlertTitle.setText(R.string.no_active_alerts)
                        b.textViewAlertDesc.setText(R.string.all_clear_no_incidents)
                        b.textViewAlertUpdated.text = ""
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FloodWatch", "Alerts Error: ${e.message}")
                _binding?.let { b ->
                    b.textViewActiveAlerts.setText(R.string.value_unavailable)
                    b.textViewEvacStatus.setText(R.string.status_unknown)
                    b.textViewEvacStatus.setTextColor(Color.GRAY)
                    b.textViewAlertTitle.setText(R.string.alerts_unavailable)
                    b.textViewAlertDesc.setText(R.string.check_connection_try_again)
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
            setTextColor(color.toColorInt())
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
            b.textViewStreetStatus.setTextColor(risk.color.toColorInt())
            b.progressBarRisk.progress = risk.progress
            b.progressBarRisk.progressTintList =
                android.content.res.ColorStateList.valueOf(risk.color.toColorInt())
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

    private fun fetchPermanentFloodZones(forceRefresh: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                latestPermanentZones = FloodRepository.permanentFloodZones(forceRefresh)
                renderPermanentFloodZones()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("FloodWatch", "Permanent flood zones unavailable", error)
            }
        }
    }

    private fun switchWeatherLayer(layer: String) {
        addWeatherOverlay(layer)
        updateWeatherLayerButtons(layer)
        updateLastUpdated()
    }

    private fun updateWeatherLayerButtons(layer: String) {
        val inactive = ColorStateList.valueOf("#475569".toColorInt())
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
            ColorStateList.valueOf(selectedColor.toColorInt())
    }

    private fun updateLastUpdated() {
        val sdf = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault())
        _binding?.textLastUpdated?.text = getString(R.string.last_updated, sdf.format(Date()))
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

    private fun showFloodReportDetails(report: FloodReport) {
        val content = layoutInflater.inflate(R.layout.dialog_flood_report_details, null)
        val image = content.findViewById<ImageView>(R.id.reportDetailsImage)
        val level = report.floodLevel?.uppercase(Locale.getDefault())
            ?: getString(R.string.value_not_available)
        val severity = report.severity ?: when (level) {
            "CRITICAL" -> 4
            "HIGH" -> 3
            "MODERATE", "MEDIUM" -> 2
            "LOW" -> 1
            else -> 0
        }

        content.findViewById<TextView>(R.id.reportDetailsLevel).text = getString(
            R.string.report_details_level_severity,
            level,
            severity
        )
        content.findViewById<TextView>(R.id.reportDetailsAddress).text =
            report.address?.takeIf { it.isNotBlank() } ?: getString(R.string.address_unavailable)
        content.findViewById<TextView>(R.id.reportDetailsPassability).text = getString(
            R.string.report_details_passability,
            report.passability?.takeIf { it.isNotBlank() } ?: getString(R.string.status_unknown)
        )
        content.findViewById<TextView>(R.id.reportDetailsDepth).apply {
            val estimatedDepth = report.wheelEstimatedDepthCm
            visibility = if (estimatedDepth != null) View.VISIBLE else View.GONE
            if (estimatedDepth != null) {
                text = getString(R.string.report_details_estimated_depth, estimatedDepth)
            }
        }
        content.findViewById<TextView>(R.id.reportDetailsDescription).apply {
            val details = report.description?.takeIf { it.isNotBlank() }
            visibility = if (details != null) View.VISIBLE else View.GONE
            text = details.orEmpty()
        }
        content.findViewById<TextView>(R.id.reportDetailsCoordinates).text = getString(
            R.string.report_details_coordinates,
            report.latitude,
            report.longitude
        )
        content.findViewById<TextView>(R.id.reportDetailsDate).text = getString(
            R.string.report_details_reported,
            report.createdAt?.let(ReportTimestampFormatter::format)
                ?: getString(R.string.value_not_available)
        )

        // The dashboard shows the clean capture. My Reports continues using
        // imageUrl, which is the evidence copy with the location/time overlay.
        val imageUrl = report.originalImageUrl?.takeIf { it.isNotBlank() }
            ?: report.imageUrl?.takeIf { it.isNotBlank() }
        image.visibility = if (imageUrl != null) View.VISIBLE else View.GONE
        if (imageUrl != null) {
            Glide.with(this)
                .load(imageUrl)
                .override(1200, 900)
                .centerCrop()
                .into(image)
        }

        MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_FloodWatch_MaterialAlertDialog
        )
            .setTitle(R.string.verified_flood_report)
            .setView(content)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun renderFloodReference(reports: List<FloodReport>) {
        floodReferenceCircle?.remove()
        floodReferenceCircle = null
        floodReferenceReport = null

        val candidates = reports
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .map { report ->
                val severity = reportSeverity(report)
                FloodReferenceCandidate(
                    value = report,
                    severity = severity,
                    depthCentimeters = report.wheelEstimatedDepthCm
                        ?: FloodReferenceSelector.depthForRange(
                            report.floodDepthRange ?: extractDepthRange(report.description),
                            severity
                        ),
                    elevationMeters = reportElevation(report)
                )
            }
        val reference = FloodReferenceSelector.select(candidates) ?: return
        floodReferenceReport = reference
        val severity = reportSeverity(reference)
        floodReferenceCircle = googleMap.addCircle(
            CircleOptions()
                .center(LatLng(reference.latitude, reference.longitude))
                .radius(180.0 + severity * 45.0)
                .strokeColor(Color.argb(235, 153, 27, 27))
                .strokeWidth(4f)
                .fillColor(Color.argb(90, 239, 68, 68))
                .clickable(true)
                .zIndex(20f)
        )
    }

    private fun renderPermanentFloodZones() {
        if (!::googleMap.isInitialized) return
        permanentZoneCircles.forEach(Circle::remove)
        permanentZoneCircles.clear()
        permanentZoneByCircleId.clear()

        val lowestElevationZoneId =
            PermanentFloodZoneResolver.lowestElevationZoneId(latestPermanentZones)
        latestPermanentZones.forEach { zone ->
            val state = PermanentFloodZoneResolver.displayState(zone, activeReportsForZones)
            val style = when (state) {
                PermanentZoneDisplayState.LOW_HAZARD ->
                    ZoneStyle(Color.argb(235, 250, 204, 21), Color.argb(45, 250, 204, 21))
                PermanentZoneDisplayState.MEDIUM_HAZARD ->
                    ZoneStyle(Color.argb(245, 249, 115, 22), Color.argb(75, 249, 115, 22))
                PermanentZoneDisplayState.HIGH_HAZARD ->
                    ZoneStyle(Color.argb(245, 239, 68, 68), Color.argb(90, 239, 68, 68))
                PermanentZoneDisplayState.DEEP_VERIFIED ->
                    ZoneStyle(Color.argb(255, 127, 29, 29), Color.argb(120, 127, 29, 29))
            }
            val circle = googleMap.addCircle(
                CircleOptions()
                    .center(LatLng(zone.latitude, zone.longitude))
                    .radius(zone.radiusMeters.toDouble())
                    .strokeColor(style.strokeColor)
                    .strokeWidth(
                        if (
                            state == PermanentZoneDisplayState.DEEP_VERIFIED ||
                            zone.id == lowestElevationZoneId
                        ) 6f else 4f
                    )
                    .fillColor(style.fillColor)
                    .clickable(true)
                    .zIndex(12f)
            )
            permanentZoneCircles += circle
            permanentZoneByCircleId[circle.id] = zone
        }
    }

    private fun showPermanentZoneDetails(zone: PermanentFloodZone) {
        val state = PermanentFloodZoneResolver.displayState(zone, activeReportsForZones)
        val displayStatus = when (state) {
            PermanentZoneDisplayState.LOW_HAZARD -> "LOW FLOOD HAZARD"
            PermanentZoneDisplayState.MEDIUM_HAZARD -> "MEDIUM FLOOD HAZARD"
            PermanentZoneDisplayState.HIGH_HAZARD -> "HIGH FLOOD HAZARD"
            PermanentZoneDisplayState.DEEP_VERIFIED -> "DEEP VERIFIED FLOOD"
        }
        val elevation = zone.elevationMeters?.let {
            String.format(Locale.getDefault(), "%.1f m (approximate terrain DEM)", it)
        } ?: getString(R.string.value_not_available)
        val isLowest = zone.id ==
            PermanentFloodZoneResolver.lowestElevationZoneId(latestPermanentZones)
        val elevationRank = if (isLowest) {
            "\nLowest elevation among the listed permanent zones: YES"
        } else {
            ""
        }

        MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_FloodWatch_MaterialAlertDialog
        )
            .setTitle(zone.name)
            .setMessage(
                    "Nearest street: ${zone.nearestStreet}\n" +
                    "NOAH baseline: ${zone.noahHazardLevel}\n" +
                    "Elevation reference: $elevation$elevationRank\n" +
                    "Map status: $displayStatus\n\n" +
                    "This permanent color is based on the NOAH flood-hazard baseline. " +
                    "A nearby deep verified report temporarily overrides it to dark red."
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showFloodReferenceDetails(report: FloodReport) {
        val severity = reportSeverity(report)
        val depth = report.wheelEstimatedDepthCm?.let {
            getString(R.string.flood_reference_exact_depth, it)
        } ?: report.floodDepthRange
            ?: extractDepthRange(report.description)
            ?: report.floodLevel
            ?: getString(R.string.value_not_available)
        val elevation = reportElevation(report)?.let {
            getString(
                R.string.flood_reference_elevation_value,
                it,
                report.elevationSource?.replace('_', ' ').orEmpty()
            )
        } ?: getString(R.string.flood_reference_elevation_unavailable)

        MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_FloodWatch_MaterialAlertDialog
        )
            .setTitle(R.string.flood_reference_title)
            .setMessage(
                getString(
                    R.string.flood_reference_details,
                    report.floodLevel ?: getString(R.string.value_not_available),
                    severity,
                    depth,
                    elevation
                )
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun reportSeverity(report: FloodReport): Int = report.severity ?: when (
        report.floodLevel?.uppercase()
    ) {
        "CRITICAL" -> 4
        "HIGH" -> 3
        "MODERATE", "MEDIUM" -> 2
        else -> 1
    }

    private fun extractDepthRange(description: String?): String? = description
        ?.let { DEPTH_RANGE_REGEX.find(it)?.groupValues?.getOrNull(1) }

    private fun reportElevation(report: FloodReport): Double? = report.elevationMeters
        ?: report.description?.let {
            ELEVATION_REGEX.find(it)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        }

    private fun observeReportChanges() {
        reportsRealtimeJob?.cancel()
        reportsRealtimeJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val channel = SupabaseClient.client.channel("dashboard-report-markers")
                reportsRealtimeChannel = channel
                val inserts = channel
                    .postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                        table = "flood_reports"
                    }.map { Unit }
                val updates = channel
                    .postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                        table = "flood_reports"
                    }.map { Unit }
                val deletes = channel
                    .postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                        table = "flood_reports"
                    }.map { Unit }
                channel.subscribe()
                merge(inserts, updates, deletes).collect {
                    FloodRepository.invalidate()
                    if (_binding != null && ::googleMap.isInitialized) {
                        fetchFloodReports(forceRefresh = true)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("FloodWatch", "Realtime marker refresh unavailable", error)
            }
        }
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
            // Do not reuse a marker cache after returning from the admin app or
            // background; a report may have been trashed while this screen slept.
            FloodRepository.invalidate()
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
        floodReportMarkers.clear()
        permanentZoneCircles.clear()
        permanentZoneByCircleId.clear()
        latestPermanentZones = emptyList()
        activeReportsForZones = emptyList()
        floodReferenceCircle = null
        floodReferenceReport = null
        reportsRealtimeJob?.cancel()
        reportsRealtimeJob = null
        reportsRealtimeChannel?.let { channel ->
            lifecycleScope.launch { runCatching { channel.unsubscribe() } }
        }
        reportsRealtimeChannel = null
        _binding?.mapView?.onDestroy()
        _binding = null
        super.onDestroyView()
    }
    override fun onLowMemory() {
        super.onLowMemory()
        _binding?.mapView?.onLowMemory()
    }
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

    private data class ZoneStyle(val strokeColor: Int, val fillColor: Int)

    companion object {
        private const val METERS_PER_SECOND_TO_KPH = 3.6
        private const val REPORT_ACTIVE_WINDOW_MS = 24L * 60L * 60L * 1000L
        private val DEPTH_RANGE_REGEX =
            Regex("Flood depth:\\s*([0-9]+(?:-[0-9]+|\\+)\\s*cm)", RegexOption.IGNORE_CASE)
        private val ELEVATION_REGEX =
            Regex("Elevation:\\s*(-?[0-9]+(?:\\.[0-9]+)?)\\s*m", RegexOption.IGNORE_CASE)
    }
}
