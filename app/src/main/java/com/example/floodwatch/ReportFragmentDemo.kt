package com.example.floodwatch

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

class ReportFragmentDemo : Fragment(), OnMapReadyCallback {

    private var capturedBitmap: Bitmap? = null
    private lateinit var textViewAddress: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mGoogleMap: GoogleMap? = null
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0
    private var realtimeChannel: RealtimeChannel? = null
    private var locationDetected = false
    private var btnAutoDetect: MaterialButton? = null
    private var locationCallback: LocationCallback? = null
    private lateinit var radioGroupFloodLevel: RadioGroup
    private lateinit var textVehiclePassability: TextView

    // ✅ BAGO — Uri para sa full resolution photo
    private lateinit var photoUri: Uri

    // ── Camera permission launcher ──────────────────────────
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) launchCamera()
        else Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
    }

    // ✅ BAGO — Full resolution camera (hindi na TakePicturePreview)
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // I-decode ang full resolution photo mula sa file
            val bitmap = BitmapFactory.decodeStream(
                requireContext().contentResolver.openInputStream(photoUri)
            )
            capturedBitmap = bitmap
            Toast.makeText(requireContext(), "✅ Photo captured!", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Location permission launcher ────────────────────────
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentLocation()
        } else {
            resetAutoDetectButton()
            Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // ────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_report_demo, container, false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        textViewAddress = view.findViewById(R.id.textViewAddress)
        btnAutoDetect = view.findViewById(R.id.buttonAutoDetect)
        radioGroupFloodLevel = view.findViewById(R.id.radioGroupFloodLevel)
        textVehiclePassability = view.findViewById(R.id.textVehiclePassability)
        setupFloodParameterDemo()

        view.findViewById<MaterialButton>(R.id.buttonGallery).setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                launchCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        view.findViewById<MaterialButton>(R.id.buttonSubmit).setOnClickListener { submitReport() }
        btnAutoDetect?.setOnClickListener { checkLocationPermission() }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        if (mapFragment != null) {
            mapFragment.getMapAsync(this)
        } else {
            Log.e("FloodWatch", "❌ SupportMapFragment is NULL!")
        }
    }

    // ✅ BAGO — Gumagawa ng temp file at binubuksan ang native camera app
    private fun launchCamera() {
        val photoFile = File.createTempFile(
            "flood_${System.currentTimeMillis()}",
            ".jpg",
            requireContext().cacheDir
        )
        photoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            photoFile
        )
        cameraLauncher.launch(photoUri)
    }

    // ── Map ready ────────────────────────────────────────────
    override fun onMapReady(googleMap: GoogleMap) {
        Log.d("FloodWatch", "✅ onMapReady called!")
        mGoogleMap = googleMap
        mGoogleMap?.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(AppLocation.LAT, AppLocation.LNG),
                AppLocation.DEFAULT_ZOOM
            )
        )
        setupRealtime()
    }

    // ── Realtime flood report markers ────────────────────────
    private fun setupRealtime() {
        lifecycleScope.launch {
            try {
                realtimeChannel = SupabaseClient.client.channel("reports-channel")
                val broadcastFlow = realtimeChannel!!
                    .postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                        table = "flood_reports"
                    }
                realtimeChannel!!.subscribe()

                broadcastFlow.collect { action ->
                    val data = action.record
                    val lat = data["latitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val lng = data["longitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val address = data["address"]?.jsonPrimitive?.contentOrNull ?: "New Flood Report"

                    activity?.runOnUiThread {
                        val loc = LatLng(lat, lng)
                        mGoogleMap?.addMarker(MarkerOptions().position(loc).title(address))
                        Toast.makeText(requireContext(), "🚨 New report: $address", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("FloodWatch", "Realtime error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // ── Unsubscribe on destroy ───────────────────────────────
    override fun onDestroyView() {
        super.onDestroyView()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        lifecycleScope.launch {
            try { realtimeChannel?.unsubscribe() } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Location permission check ────────────────────────────
    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Toast.makeText(
                    requireContext(),
                    "Location permission is needed to auto-detect your area.",
                    Toast.LENGTH_LONG
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // ── Get current location (with fallback) ─────────────────
    private fun getCurrentLocation() {
        btnAutoDetect?.isEnabled = false
        btnAutoDetect?.text = "Detecting…"
        textViewAddress.text = "Getting your location…"

        val locationManager = requireContext()
            .getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            textViewAddress.text = "Could not detect location. Enable GPS."
            resetAutoDetectButton()
            showEnableGpsDialog()
            return
        }

        try {
            val cts = CancellationTokenSource()
            fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        onLocationReceived(location.latitude, location.longitude)
                    } else {
                        getLastKnownLocation()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FloodWatch", "getCurrentLocation failed: ${e.message}")
                    getLastKnownLocation()
                }
        } catch (e: SecurityException) {
            textViewAddress.text = "Location permission error"
            resetAutoDetectButton()
            e.printStackTrace()
        }
    }

    // ── Fallback: last known location ────────────────────────
    private fun getLastKnownLocation() {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        onLocationReceived(location.latitude, location.longitude)
                        Toast.makeText(
                            requireContext(),
                            "Using last known location",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        requestSingleLocationUpdate()
                    }
                }
                .addOnFailureListener {
                    textViewAddress.text = "Location error. Try again."
                    resetAutoDetectButton()
                }
        } catch (e: SecurityException) {
            resetAutoDetectButton()
            e.printStackTrace()
        }
    }

    // ── Final fallback: request live GPS update ───────────────
    private fun requestSingleLocationUpdate() {
        try {
            textViewAddress.text = "Waiting for GPS signal…"

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 1000L
            ).setMaxUpdates(1).build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)
                    locationCallback = null
                    val location = result.lastLocation
                    if (location != null) {
                        onLocationReceived(location.latitude, location.longitude)
                    } else {
                        if (isAdded) {
                            textViewAddress.text = "Could not detect location. Enable GPS."
                            resetAutoDetectButton()
                            showEnableGpsDialog()
                        }
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )

            view?.postDelayed({
                locationCallback?.let {
                    fusedLocationClient.removeLocationUpdates(it)
                    locationCallback = null
                }
                if (!locationDetected && isAdded) {
                    textViewAddress.text = "GPS timeout. Please try again."
                    resetAutoDetectButton()
                }
            }, 15_000L)

        } catch (e: SecurityException) {
            resetAutoDetectButton()
            showEnableGpsDialog()
            e.printStackTrace()
        }
    }

    // ── Location received — update map + address ─────────────
    private fun onLocationReceived(lat: Double, lng: Double) {
        currentLat = lat
        currentLng = lng
        locationDetected = true

        val userLatLng = LatLng(lat, lng)
        mGoogleMap?.clear()
        mGoogleMap?.addMarker(MarkerOptions().position(userLatLng).title("Your Location"))
        mGoogleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 17f))

        btnAutoDetect?.isEnabled = true
        btnAutoDetect?.text = "Re-detect Location"

        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        if (Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocation(lat, lng, 1) { addresses ->
                activity?.runOnUiThread {
                    textViewAddress.text = if (addresses.isNotEmpty())
                        addresses[0].getAddressLine(0)
                    else
                        "Lat: %.5f, Lng: %.5f".format(lat, lng)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            textViewAddress.text = if (!addresses.isNullOrEmpty())
                addresses[0].getAddressLine(0)
            else
                "Lat: %.5f, Lng: %.5f".format(lat, lng)
        }
    }

    // ── GPS dialog ───────────────────────────────────────────
    private fun showEnableGpsDialog() {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Enable GPS")
            .setMessage("Your GPS appears to be off. Please enable Location Services to auto-detect your area.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Reset button state ───────────────────────────────────
    private fun resetAutoDetectButton() {
        btnAutoDetect?.isEnabled = true
        btnAutoDetect?.text = "Auto Detect Location"
    }

    private fun setupFloodParameterDemo() {
        updateVehiclePassability(selectedFloodDepthRange())
        radioGroupFloodLevel.setOnCheckedChangeListener { _, _ ->
            updateVehiclePassability(selectedFloodDepthRange())
        }
    }

    private fun selectedFloodLevel(): String {
        return when (selectedFloodDepthRange()) {
            "6-15 cm" -> "LOW"
            "16-30 cm" -> "MODERATE"
            "31-50 cm" -> "HIGH"
            "50+ cm" -> "CRITICAL"
            else -> "SAFE"
        }
    }

    private fun selectedFloodDepthRange(): String {
        return when (radioGroupFloodLevel.checkedRadioButtonId) {
            R.id.radioDepthMotorcycle -> "6-15 cm"
            R.id.radioDepthSmallCars -> "16-30 cm"
            R.id.radioDepthLargeVehicles -> "31-50 cm"
            R.id.radioDepthClosed -> "50+ cm"
            else -> "0-5 cm"
        }
    }

    private fun vehiclePassabilityFor(depthRange: String): String {
        return when (depthRange) {
            "6-15 cm" -> "Motorcycles not advised"
            "16-30 cm" -> "Small cars not advised"
            "31-50 cm" -> "Only large vehicles"
            "50+ cm" -> "Not passable"
            else -> "Passable"
        }
    }

    private fun updateVehiclePassability(depthRange: String) {
        textVehiclePassability.text = "$depthRange: ${vehiclePassabilityFor(depthRange)}"
    }

    // ── Submit report ────────────────────────────────────────
    private fun submitReport() {
        if (capturedBitmap == null) {
            Toast.makeText(requireContext(), "Please take a photo first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!locationDetected) {
            Toast.makeText(requireContext(), "Please detect your location first", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "Please login to submit a report", Toast.LENGTH_SHORT).show()
            return
        }

        val progressBar = view?.findViewById<ProgressBar>(R.id.progressBar)
        val btnSubmit = view?.findViewById<MaterialButton>(R.id.buttonSubmit)

        lifecycleScope.launch {
            try {
                progressBar?.visibility = View.VISIBLE
                btnSubmit?.isEnabled = false
                btnSubmit?.text = "Submitting…"

                // ✅ 90% quality para malinaw pero hindi masyadong malaki ang file
                val outputStream = ByteArrayOutputStream()
                capturedBitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                val bytes = outputStream.toByteArray()

                val fileName = "report_${System.currentTimeMillis()}.jpg"
                val bucket = SupabaseClient.client.storage.from("flood-reports")
                bucket.upload(path = fileName, data = bytes)
                val imageUrl = bucket.publicUrl(fileName)

                val selectedLevel = selectedFloodLevel()
                val selectedDepthRange = selectedFloodDepthRange()
                val vehicleGuidance = vehiclePassabilityFor(selectedDepthRange)

                val reportData = FloodReport(
                    userId = currentUserId,
                    imageUrl = imageUrl,
                    address = textViewAddress.text.toString(),
                    latitude = currentLat,
                    longitude = currentLng,
                    floodLevel = selectedLevel,
                    description = "Flood depth: $selectedDepthRange. Vehicle guidance: $vehicleGuidance"
                )

                SupabaseClient.client.postgrest.from("flood_reports").insert(reportData)

                if (isAdded) {
                    Toast.makeText(requireContext(), "✅ Report submitted!", Toast.LENGTH_LONG).show()
                    capturedBitmap = null
                    locationDetected = false
                    textViewAddress.text = "Press 'Auto Detect' to get your location"
                    btnAutoDetect?.text = "Auto Detect Location"
                    mGoogleMap?.clear()
                    mGoogleMap?.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(AppLocation.LAT, AppLocation.LNG),
                            AppLocation.DEFAULT_ZOOM
                        )
                    )
                }

            } catch (e: Exception) {
                Log.e("FloodWatch", "Submit error: ${e.message}")
                if (isAdded) {
                    Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                progressBar?.visibility = View.GONE
                btnSubmit?.isEnabled = true
                btnSubmit?.text = "Submit Report"
            }
        }
    }
}
