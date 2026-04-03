package com.example.floodwatch

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.android.gms.maps.model.UrlTileProvider
import com.google.android.material.button.MaterialButton
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private var currentOverlay: TileOverlay? = null

    private lateinit var lastUpdatedText: TextView

    // OpenWeatherMap API key
    private val apiKey: String = BuildConfig.OPENWEATHER_API_KEY

    // Handler for auto-refresh
    private val refreshInterval = 3 * 60 * 1000L // 3 minutes
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            currentOverlay?.let {
                it.remove() // remove old overlay
                addWeatherOverlay(currentLayer)
                updateLastUpdated() // update timestamp
            }
            handler.postDelayed(this, refreshInterval)
        }
    }

    private var currentLayer = "precipitation_new"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize MapView
        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        // Last updated TextView
        lastUpdatedText = view.findViewById(R.id.textLastUpdated)

        // Zoom buttons
        val buttonZoomIn: TextView = view.findViewById(R.id.buttonZoomIn)
        val buttonZoomOut: TextView = view.findViewById(R.id.buttonZoomOut)
        buttonZoomIn.setOnClickListener { googleMap.animateCamera(CameraUpdateFactory.zoomIn()) }
        buttonZoomOut.setOnClickListener { googleMap.animateCamera(CameraUpdateFactory.zoomOut()) }

        // Weather layer buttons
        view.findViewById<MaterialButton>(R.id.buttonRainLayer)?.setOnClickListener {
            switchWeatherLayer("precipitation_new")
        }
        view.findViewById<MaterialButton>(R.id.buttonTempLayer)?.setOnClickListener {
            switchWeatherLayer("temp_new")
        }
        view.findViewById<MaterialButton>(R.id.buttonCloudLayer)?.setOnClickListener {
            switchWeatherLayer("clouds_new")
        }

        return view
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Center map on the Philippines
        val philippines = LatLng(12.8797, 121.7740)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(philippines, 5f))

        // Default weather layer: Rain
        addWeatherOverlay(currentLayer)
        updateLastUpdated()

        // Start auto-refresh
        handler.postDelayed(refreshRunnable, refreshInterval)
    }

    private fun addWeatherOverlay(layer: String) {
        currentLayer = layer
        val tileProvider = object : UrlTileProvider(256, 256) {
            override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
                return try {
                    URL("https://tile.openweathermap.org/map/$layer/$zoom/$x/$y.png?appid=$apiKey")
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

        currentOverlay?.remove()
        currentOverlay = googleMap.addTileOverlay(TileOverlayOptions().tileProvider(tileProvider))
    }

    private fun switchWeatherLayer(layer: String) {
        addWeatherOverlay(layer)
        updateLastUpdated()
    }

    private fun updateLastUpdated() {
        val sdf = SimpleDateFormat("HH:mm:ss, dd MMM yyyy", Locale.getDefault())
        val currentTime = sdf.format(Date())
        lastUpdatedText.text = getString(R.string.last_updated, currentTime)
    }

    // MapView lifecycle
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy(); handler.removeCallbacks(refreshRunnable) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
