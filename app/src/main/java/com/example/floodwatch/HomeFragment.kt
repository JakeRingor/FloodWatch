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

    // [SETTING] Fixed Coordinates para sa Kingsville, Antipolo
    // Latitude at Longitude na mismo ng Mayamot area para accurate ang wind speed
    private val KINGSVILLE = LatLng(14.6225, 121.1245)

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
        // I-set ang camera focus sa Kingsville
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(KINGSVILLE, 15f))
        binding.textViewStatus.text = "Kingsville • Rizal Weather"

        refreshData()
        handler.postDelayed(refreshRunnable, refreshInterval)
    }

    private fun refreshData() {
        addWeatherOverlay(currentLayer)
        updateLastUpdated()
        fetchFloodReports()
        // Hyper-local weather data fetch
        fetchWeatherData(KINGSVILLE.latitude, KINGSVILLE.longitude)
    }

    private fun fetchWeatherData(lat: Double, lon: Double) {
        lifecycleScope.launch {
            try {
                val response = weatherApi.getWeather(lat, lon, apiKey)
                _binding?.let { b ->
                    // 1. Temperature logic
                    val currentTemp = response.main.temp
                    b.textViewTemp.text = "${currentTemp.toInt()}°C"

                    // 2. Wind Speed Conversion (m/s to km/h)
                    // Mas lalapit ito sa Google Weather dahil tama na ang coordinates
                    val windKmH = response.wind.speed * 3.6
                    b.textViewWindSpeed.text = windKmH.toInt().toString()


                    // 3. Rainfall (Volume for the past 1 hour)
                    val rainVal = response.rain?.oneHour ?: 0.0
                    b.textViewRainfall.text = rainVal.toInt().toString()

                    // 4. Labels & Condition
                    b.textViewCondition.text = response.weather.firstOrNull()?.main ?: "--"
                    b.textViewHumidity.text = "${response.main.humidity}% Humidity"

                    // 5. Dynamic Icon Logic (Priority to 32°C Heat)
                    val condition = response.weather.firstOrNull()?.main ?: ""
                    when {
                        currentTemp >= 32.0 -> b.imageViewWeatherIcon.setImageResource(R.drawable.ic_sun)
                        condition.contains("Rain", true) -> b.imageViewWeatherIcon.setImageResource(R.drawable.ic_rain)
                        condition.contains("Cloud", true) -> b.imageViewWeatherIcon.setImageResource(R.drawable.ic_cloud)
                        condition.contains("Clear", true) -> b.imageViewWeatherIcon.setImageResource(R.drawable.ic_sun)
                        else -> b.imageViewWeatherIcon.setImageResource(R.drawable.ic_sun)
                    }
                }
            } catch (e: Exception) {
                Log.e("WeatherAPI", "Error: ${e.message}")
            }
        }
    }

    private fun fetchFloodReports() {
        lifecycleScope.launch {
            try {
                val reports = SupabaseClient.client.postgrest
                    .from("reports")
                    .select()
                    .decodeList<FloodReport>()

                _binding?.let { b ->
                    val count = reports.size
                    b.textViewActiveAlerts.text = String.format("%02d", count)

                    if (count > 0) {
                        b.textViewEvacStatus.text = "READY"
                        b.textViewEvacStatus.setTextColor(android.graphics.Color.parseColor("#0EA5E9"))
                    } else {
                        b.textViewEvacStatus.text = "NONE"
                        b.textViewEvacStatus.setTextColor(android.graphics.Color.GRAY)
                    }
                }

                googleMap.clear()
                reports.forEach { report ->
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(report.latitude, report.longitude))
                            .title("Flood Incident")
                            .snippet(report.address)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    )
                }
            } catch (e: Exception) {
                Log.e("FloodWatch", "Supabase Error: ${e.message}")
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

    override fun onResume() { super.onResume(); binding.mapView.onResume() }
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