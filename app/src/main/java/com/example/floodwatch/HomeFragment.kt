package com.example.floodwatch

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.floodwatch.databinding.FragmentHomeBinding
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var googleMap: GoogleMap
    private var currentOverlay: TileOverlay? = null

    private val apiKey: String = BuildConfig.OPENWEATHER_API_KEY
    private var currentLayer = "precipitation_new"

    // [SETTING 1] Coordinates para sa Kingsville (Para sa Map View)
    private val KINGSVILLE = LatLng(14.6225, 121.1245)

    // [SETTING 2] Coordinates para sa Rizal Province (Para sa Weather Data)
    private val RIZAL_LAT = 14.5845
    private val RIZAL_LON = 121.1754

    private val weatherApi: OpenWeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenWeatherApi::class.java)
    }

    private val refreshInterval = 3 * 60 * 1000L
    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (::googleMap.isInitialized) {
                refreshData()
            }
            handler.postDelayed(this, refreshInterval)
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
    }

    private fun setupClickListeners() {
        binding.buttonZoomIn.setOnClickListener { if (::googleMap.isInitialized) googleMap.animateCamera(CameraUpdateFactory.zoomIn()) }
        binding.buttonZoomOut.setOnClickListener { if (::googleMap.isInitialized) googleMap.animateCamera(CameraUpdateFactory.zoomOut()) }
        binding.buttonRainLayer.setOnClickListener { switchWeatherLayer("precipitation_new") }
        binding.buttonTempLayer.setOnClickListener { switchWeatherLayer("temp_new") }
        binding.buttonCloudLayer.setOnClickListener { switchWeatherLayer("clouds_new") }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(KINGSVILLE, 15f))
        binding.textViewStatus.text = "Kingsville • Rizal Weather"
        refreshData()
        handler.postDelayed(refreshRunnable, refreshInterval)
    }

    private fun refreshData() {
        addWeatherOverlay(currentLayer)
        updateLastUpdated()
        fetchFloodReports()
        fetchFloodAlerts()                          // ✅ BAGONG DAGDAG
        fetchWeatherData(RIZAL_LAT, RIZAL_LON)
    }

    private fun fetchWeatherData(lat: Double, lon: Double) {
        lifecycleScope.launch {
            try {
                val response = weatherApi.getWeather(lat, lon, apiKey)
                _binding?.let { b ->
                    b.textViewTemp.text = "${response.main.temp.toInt()}°C"
                    b.textViewCondition.text = response.weather.firstOrNull()?.main ?: "--"
                    b.textViewHumidity.text = "${response.main.humidity}% Humidity"
                    b.textViewWindSpeed.text = response.wind.speed.toInt().toString()

                    val rainVal = response.rain?.oneHour ?: 0.0
                    b.textViewRainfall.text = rainVal.toInt().toString()

                    val temp = response.main.temp
                    val condition = response.weather.firstOrNull()?.main ?: ""

                    when {


                        temp >= 32 -> {
                            b.imageViewWeatherIcon.setImageResource(R.drawable.ic_sun)
                        }

                        // 🌧 Rain
                        condition.contains("Rain", true) -> {
                            b.imageViewWeatherIcon.setImageResource(R.drawable.ic_rain)
                        }

                        // ☁ Cloud
                        condition.contains("Cloud", true) -> {
                            b.imageViewWeatherIcon.setImageResource(R.drawable.ic_cloud)
                        }

                        // ☀ Clear
                        condition.contains("Clear", true) -> {
                            b.imageViewWeatherIcon.setImageResource(R.drawable.ic_sun)
                        }

                        // default
                        else -> {
                            b.imageViewWeatherIcon.setImageResource(R.drawable.ic_sun)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WeatherAPI", "Error: ${e.message}")
            }
        }
    }

    // ✅ FIX: "reports" → "flood_reports"
    private fun fetchFloodReports() {
        lifecycleScope.launch {
            try {
                val reports = SupabaseClient.client.postgrest
                    .from("flood_reports")
                    .select()
                    .decodeList<FloodReport>()

                Log.d("FloodWatch", "Total reports: ${reports.size}")
                reports.forEach { Log.d("FloodWatch", "Status: ${it.status}") }

                // ✅ TAMA — uppercase() compare sa "PENDING"
                val count = reports.count { it.status.uppercase() == "PENDING" }

                Log.d("FloodWatch", "PENDING count: $count")

                _binding?.let { b ->
                    b.textViewActiveAlerts.text = String.format("%02d", count)
                    if (count > 0) {
                        b.textViewEvacStatus.text = "READY"
                        b.textViewEvacStatus.setTextColor(
                            android.graphics.Color.parseColor("#0EA5E9")
                        )
                    } else {
                        b.textViewEvacStatus.text = "NONE"
                        b.textViewEvacStatus.setTextColor(android.graphics.Color.GRAY)
                    }
                }

                googleMap.clear()
                reports.filter { it.status.uppercase() == "PENDING" }.forEach { report ->
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(report.latitude, report.longitude))
                            .title("Flood Incident")
                            .snippet(report.address)
                            .icon(BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_BLUE))
                    )
                }

            } catch (e: Exception) {
                Log.e("FloodWatch", "Supabase Error: ${e.message}")
            }
        }
    }

    // ✅ BAGONG FUNCTION — kumukuha ng active alerts mula flood_alerts table
    private fun fetchFloodAlerts() {
        lifecycleScope.launch {
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
                    if (alerts.isNotEmpty()) {
                        val latest = alerts.first()
                        b.textViewAlertTitle.text = latest.title
                        b.textViewAlertDesc.text  = latest.message  // ✅ "message" hindi "description"
                    } else {
                        b.textViewAlertTitle.text = "No active alerts"
                        b.textViewAlertDesc.text  = "All clear. No flood incidents reported."
                    }
                }
            } catch (e: Exception) {
                Log.e("FloodWatch", "Alerts Error: ${e.message}")
            }
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
        currentOverlay = googleMap.addTileOverlay(TileOverlayOptions().tileProvider(tileProvider).transparency(0.3f))
    }

    private fun switchWeatherLayer(layer: String) {
        addWeatherOverlay(layer)
        updateLastUpdated()
    }

    private fun updateLastUpdated() {
        val sdf = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault())
        _binding?.textLastUpdated?.text = "Last updated: ${sdf.format(Date())}"
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (::googleMap.isInitialized) {
            fetchFloodReports()
            fetchFloodAlerts()
        }
    }
    override fun onPause() { super.onPause(); binding.mapView.onPause() }
    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(refreshRunnable)
        binding.mapView.onDestroy()
        _binding = null
    }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.mapView?.onSaveInstanceState(outState)
    }
}