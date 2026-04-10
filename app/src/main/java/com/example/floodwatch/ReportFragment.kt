package com.example.floodwatch

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.*

class ReportFragment : Fragment(), OnMapReadyCallback {

    private var selectedImageUri: Uri? = null
    private lateinit var textViewAddress: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var mGoogleMap: GoogleMap? = null
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0

    // Launcher para sa Gallery
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedImageUri = result.data?.data
                Toast.makeText(requireContext(), "Image Selected!", Toast.LENGTH_SHORT).show()
            }
        }

    // Launcher para sa Location Permission
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) getCurrentLocation() else Toast.makeText(requireContext(), "Location Permission Denied", Toast.LENGTH_SHORT).show()
        }

    // Launcher para sa Storage/Gallery Permission (Fix para sa crashes)
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) openGallery() else Toast.makeText(requireContext(), "Storage Permission Denied", Toast.LENGTH_SHORT).show()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_report, container, false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        textViewAddress = view.findViewById(R.id.textViewAddress)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        // FIX: Check permission muna bago mag-gallery
        view.findViewById<MaterialButton>(R.id.buttonGallery).setOnClickListener {
            checkStoragePermission()
        }

        view.findViewById<MaterialButton>(R.id.buttonSubmit).setOnClickListener { submitReport() }
        view.findViewById<MaterialButton>(R.id.buttonAutoDetect).setOnClickListener { checkLocationPermission() }

        return view
    }

    private fun checkStoragePermission() {
        val permissionNeeded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permissionNeeded) == PackageManager.PERMISSION_GRANTED) {
            openGallery()
        } else {
            storagePermissionLauncher.launch(permissionNeeded)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap
        val defaultLoc = LatLng(14.5874, 121.1758)
        mGoogleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 10f))
        setupRealtime()
    }

    private fun setupRealtime() {
        lifecycleScope.launch {
            try {
                val realtime = SupabaseClient.client.realtime
                val myChannel = realtime.channel("reports-channel")
                val broadcastFlow = myChannel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "reports" }
                myChannel.subscribe()

                broadcastFlow.collect { action ->
                    val data = action.record
                    val lat = data["latitude"]?.toString()?.toDouble() ?: 0.0
                    val lng = data["longitude"]?.toString()?.toDouble() ?: 0.0
                    val address = data["address"]?.toString() ?: "New Flood Alert"

                    activity?.runOnUiThread {
                        if (isAdded) {
                            val newMarkerLoc = LatLng(lat, lng)
                            mGoogleMap?.addMarker(MarkerOptions().position(newMarkerLoc).title(address))
                            Toast.makeText(requireContext(), "Live Alert: $address", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun getCurrentLocation() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        currentLat = location.latitude
                        currentLng = location.longitude
                        val userLatLng = LatLng(currentLat, currentLng)

                        mGoogleMap?.addMarker(MarkerOptions().position(userLatLng).title("Your Location"))
                        mGoogleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 17f))

                        val geocoder = Geocoder(requireContext(), Locale.getDefault())
                        if (Build.VERSION.SDK_INT >= 33) {
                            geocoder.getFromLocation(currentLat, currentLng, 1) { addresses ->
                                if (addresses.isNotEmpty()) {
                                    activity?.runOnUiThread { textViewAddress.text = addresses[0].getAddressLine(0) }
                                }
                            }
                        } else {
                            // FIX: Thread-safe UI update para sa lower versions
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(currentLat, currentLng, 1)
                            if (!addresses.isNullOrEmpty()) {
                                activity?.runOnUiThread { textViewAddress.text = addresses[0].getAddressLine(0) }
                            }
                        }
                    }
                }
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    private fun submitReport() {
        val imageUri = selectedImageUri ?: return Toast.makeText(requireContext(), "Select an image first", Toast.LENGTH_SHORT).show()
        if (currentLat == 0.0) return Toast.makeText(requireContext(), "Detect location first", Toast.LENGTH_SHORT).show()

        val currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "Please login to report", Toast.LENGTH_SHORT).show()
            return
        }

        val progressBar = view?.findViewById<ProgressBar>(R.id.progressBar)
        val btnSubmit = view?.findViewById<MaterialButton>(R.id.buttonSubmit)

        lifecycleScope.launch {
            try {
                progressBar?.visibility = View.VISIBLE
                btnSubmit?.isEnabled = false

                // 1. Image Processing
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                requireContext().contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it, null, options) }
                options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
                options.inJustDecodeBounds = false

                val sampledBitmap = requireContext().contentResolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: throw Exception("Failed to load image")

                val outputStream = ByteArrayOutputStream()
                sampledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                val bytes = outputStream.toByteArray()

                // 2. Upload & Save
                val reportId = UUID.randomUUID().toString()
                val bucket = SupabaseClient.client.storage.from("flood-reports")
                bucket.upload("$reportId.jpg", bytes)
                val imageUrl = bucket.publicUrl("$reportId.jpg")

                val reportData = FloodReport(
                    id = reportId,
                    user_id = currentUserId,
                    image_url = imageUrl,
                    address = textViewAddress.text.toString(),
                    latitude = currentLat,
                    longitude = currentLng,
                    timestamp = System.currentTimeMillis()
                )

                SupabaseClient.client.postgrest.from("reports").insert(reportData)

                if (isAdded) {
                    Toast.makeText(requireContext(), "Report Submitted!", Toast.LENGTH_LONG).show()
                    selectedImageUri = null
                    textViewAddress.text = "Location recorded."
                }
            } catch (e: Exception) {
                Log.e("FloodWatch", "Error: ${e.message}")
                if (isAdded) Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar?.visibility = View.GONE
                btnSubmit?.isEnabled = true
            }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}