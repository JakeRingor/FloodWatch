package com.example.floodwatch

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.android.gms.maps.model.UrlTileProvider
import com.google.android.material.button.MaterialButton
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private var currentOverlay: TileOverlay? = null
    private lateinit var lastUpdatedText: TextView

    private val apiKey: String = BuildConfig.OPENWEATHER_API_KEY
    private var currentLayer = "precipitation_new"

    private val refreshInterval = 3 * 60 * 1000L
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            currentOverlay?.let {
                addWeatherOverlay(currentLayer)
                updateLastUpdated()
            }
            handler.postDelayed(this, refreshInterval)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        lastUpdatedText = view.findViewById(R.id.textLastUpdated)

        // Zoom buttons
        val buttonZoomIn: TextView = view.findViewById(R.id.buttonZoomIn)
        val buttonZoomOut: TextView = view.findViewById(R.id.buttonZoomOut)
        buttonZoomIn.setOnClickListener {
            if (::googleMap.isInitialized)
                googleMap.animateCamera(CameraUpdateFactory.zoomIn())
        }
        buttonZoomOut.setOnClickListener {
            if (::googleMap.isInitialized)
                googleMap.animateCamera(CameraUpdateFactory.zoomOut())
        }

        // Layer buttons
        view.findViewById<MaterialButton>(R.id.buttonRainLayer)?.setOnClickListener {
            switchWeatherLayer("precipitation_new")
        }
        view.findViewById<MaterialButton>(R.id.buttonTempLayer)?.setOnClickListener {
            switchWeatherLayer("temp_new")
        }
        view.findViewById<MaterialButton>(R.id.buttonCloudLayer)?.setOnClickListener {
            switchWeatherLayer("clouds_new")
        }

        // Debug: verify API key is loaded
        if (apiKey.isBlank() || apiKey == "null") {
            Log.e("FloodWatch", "❌ API key is empty! Check local.properties + build.gradle")
            Toast.makeText(context, "Weather API key not configured", Toast.LENGTH_LONG).show()
        } else {
            Log.d("FloodWatch", "✅ API key loaded: ${apiKey.take(6)}...")
        }

        return view
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        val philippines = LatLng(12.8797, 121.7740)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(philippines, 5f))

        addWeatherOverlay(currentLayer)
        updateLastUpdated()

        handler.postDelayed(refreshRunnable, refreshInterval)
    }

    private fun addWeatherOverlay(layer: String) {
        currentLayer = layer

        // Remove old overlay and clear its tile cache
        currentOverlay?.clearTileCache()
        currentOverlay?.remove()
        currentOverlay = null

        val tileProvider = object : UrlTileProvider(256, 256) {
            override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
                val urlString =
                    "https://tile.openweathermap.org/map/$layer/$zoom/$x/$y.png?appid=$apiKey"

                return try {
                    val url = URL(urlString)

                    // Debug: log the first tile request per layer change
                    if (x == 0 && y == 0) {
                        Log.d("FloodWatch", "🗺 Tile URL: $urlString")

                        // Check HTTP response code on a background thread (already here)
                        try {
                            val conn = url.openConnection() as HttpURLConnection
                            conn.connectTimeout = 5000
                            conn.readTimeout = 5000
                            conn.requestMethod = "GET"
                            val responseCode = conn.responseCode
                            Log.d("FloodWatch", "📡 HTTP Response: $responseCode for $layer")
                            conn.disconnect()
                        } catch (e: Exception) {
                            Log.e("FloodWatch", "❌ Connection test failed: ${e.message}")
                        }
                    }

                    url
                } catch (e: Exception) {
                    Log.e("FloodWatch", "❌ Bad tile URL: $urlString — ${e.message}")
                    null
                }
            }
        }

        currentOverlay = googleMap.addTileOverlay(
            TileOverlayOptions()
                .tileProvider(tileProvider)
                .transparency(0.2f)   // 0.0 = fully opaque, 1.0 = invisible
                .fadeIn(true)
                .zIndex(1f)
        )

        Log.d("FloodWatch", "✅ Overlay added for layer: $layer")
    }

    private fun switchWeatherLayer(layer: String) {
        addWeatherOverlay(layer)
        updateLastUpdated()
        Log.d("FloodWatch", "🔄 Switched to layer: $layer")
    }

    private fun updateLastUpdated() {
        val sdf = SimpleDateFormat("HH:mm:ss, dd MMM yyyy", Locale.getDefault())
        lastUpdatedText.text = "Last updated: ${sdf.format(Date())}"
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        handler.removeCallbacks(refreshRunnable)
    }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}