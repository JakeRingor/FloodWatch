package com.example.floodwatch

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
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
import java.text.SimpleDateFormat
import java.util.*

class ReportFragment : Fragment(), OnMapReadyCallback {

    private var capturedBitmap: Bitmap? = null
    private lateinit var textViewAddress: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mGoogleMap: GoogleMap? = null
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0
    private var realtimeChannel: RealtimeChannel? = null
    private var locationDetected = false
    private var btnAutoDetect: MaterialButton? = null

    private lateinit var photoUri: Uri

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) launchCamera()
        else Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val bitmap = BitmapFactory.decodeStream(requireContext().contentResolver.openInputStream(photoUri))
            if (bitmap != null) showImagePreviewDialog(bitmap)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) getCurrentLocation()
        else {
            resetAutoDetectButton()
            Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_report, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        textViewAddress = view.findViewById(R.id.textViewAddress)
        btnAutoDetect = view.findViewById(R.id.buttonAutoDetect)

        view.findViewById<MaterialButton>(R.id.buttonGallery).setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        view.findViewById<MaterialButton>(R.id.buttonSubmit).setOnClickListener { showReportDetailsDialog() }
        btnAutoDetect?.setOnClickListener { checkLocationPermission() }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment)?.getMapAsync(this)
    }

    private fun launchCamera() {
        val photoFile = File.createTempFile("flood_${System.currentTimeMillis()}", ".jpg", requireContext().cacheDir)
        photoUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", photoFile)
        cameraLauncher.launch(photoUri)
    }

    private fun showImagePreviewDialog(bitmap: Bitmap) {
        val previewView = ImageView(requireContext()).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            setPadding(20, 20, 20, 20)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Preview Photo")
            .setMessage("Is this photo clear? You can retake it if needed.")
            .setView(previewView)
            .setPositiveButton("Use Photo") { _, _ -> capturedBitmap = bitmap }
            .setNegativeButton("Retake") { _, _ -> launchCamera() }
            .setCancelable(false).show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap
        mGoogleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(AppLocation.LAT, AppLocation.LNG), AppLocation.DEFAULT_ZOOM))
        setupRealtime()
    }

    private fun setupRealtime() {
        lifecycleScope.launch {
            try {
                realtimeChannel = SupabaseClient.client.channel("reports-channel")
                val broadcastFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "flood_reports" }
                realtimeChannel!!.subscribe()
                broadcastFlow.collect { action ->
                    val data = action.record
                    val lat = data["latitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val lng = data["longitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val address = data["address"]?.jsonPrimitive?.contentOrNull ?: "New Flood Report"
                    activity?.runOnUiThread {
                        mGoogleMap?.addMarker(MarkerOptions().position(LatLng(lat, lng)).title(address))
                    }
                }
            } catch (e: Exception) { Log.e("FloodWatch", "Realtime Error: ${e.message}") }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) getCurrentLocation()
        else requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun getCurrentLocation() {
        btnAutoDetect?.isEnabled = false
        btnAutoDetect?.text = "Detecting…"
        val cts = CancellationTokenSource()
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location -> if (location != null) onLocationReceived(location.latitude, location.longitude) else resetAutoDetectButton() }
        } catch (e: SecurityException) { resetAutoDetectButton() }
    }

    private fun onLocationReceived(lat: Double, lng: Double) {
        currentLat = lat; currentLng = lng; locationDetected = true
        val userLatLng = LatLng(lat, lng)
        mGoogleMap?.clear(); mGoogleMap?.addMarker(MarkerOptions().position(userLatLng).title("Your Location"))
        mGoogleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 17f))
        btnAutoDetect?.isEnabled = true; btnAutoDetect?.text = "Re-detect Location"
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(lat, lng, 1) { addresses ->
                activity?.runOnUiThread { textViewAddress.text = if (addresses.isNotEmpty()) addresses[0].getAddressLine(0) else "Lat: %.5f, Lng: %.5f".format(lat, lng) }
            }
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            textViewAddress.text = if (!addresses.isNullOrEmpty()) addresses[0].getAddressLine(0) else "Lat: %.5f, Lng: %.5f".format(lat, lng)
        }
    }

    private fun resetAutoDetectButton() { btnAutoDetect?.isEnabled = true; btnAutoDetect?.text = "Auto Detect Location" }

    private fun showReportDetailsDialog() {
        if (capturedBitmap == null || !locationDetected) {
            Toast.makeText(requireContext(), "Please capture a photo and detect location first", Toast.LENGTH_SHORT).show()
            return
        }
        val levels = arrayOf("LOW", "MID", "HIGH", "CRITICAL")
        val passabilityOptions = arrayOf("PASSABLE TO ALL", "NOT FOR LIGHT VEHICLES", "NOT PASSABLE")
        var selectedLevel = levels[0]; var selectedPassability = passabilityOptions[0]
        AlertDialog.Builder(requireContext()).setTitle("Flood Level").setSingleChoiceItems(levels, 0) { _, which -> selectedLevel = levels[which] }
            .setPositiveButton("Next") { _, _ ->
                AlertDialog.Builder(requireContext()).setTitle("Vehicle Passability").setSingleChoiceItems(passabilityOptions, 0) { _, which -> selectedPassability = passabilityOptions[which] }
                    .setPositiveButton("Submit") { _, _ -> submitReport(selectedLevel, selectedPassability) }.show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun addOverlayToBitmap(bitmap: Bitmap, address: String): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            color = Color.WHITE; textSize = (bitmap.height / 35).toFloat(); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true; setShadowLayer(3f, 2f, 2f, Color.BLACK)
        }
        val timestamp = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date())
        canvas.drawText(address, 40f, result.height - 40f, paint)
        canvas.drawText(timestamp, 40f, result.height - 40f - (paint.textSize + 10), paint)
        return result
    }

    private fun submitReport(level: String, passability: String) {
        val currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return
        val progressBar = view?.findViewById<ProgressBar>(R.id.progressBar)
        val btnSubmit = view?.findViewById<MaterialButton>(R.id.buttonSubmit)

        lifecycleScope.launch {
            try {
                progressBar?.visibility = View.VISIBLE
                btnSubmit?.isEnabled = false
                val addressStr = textViewAddress.text.toString()
                val processedBitmap = addOverlayToBitmap(capturedBitmap!!, addressStr)
                val outputStream = ByteArrayOutputStream()
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                val fileName = "report_${System.currentTimeMillis()}.jpg"
                val bucket = SupabaseClient.client.storage.from("flood-reports")
                bucket.upload(path = fileName, data = outputStream.toByteArray())
                val imageUrl = bucket.publicUrl(fileName)

                val report = FloodReport(
                    userId = currentUserId, imageUrl = imageUrl, address = addressStr,
                    latitude = currentLat, longitude = currentLng, floodLevel = level,
                    passability = passability, status = "pending", severity = 1,
                    description = "User reported flood incident"
                )

                SupabaseClient.client.from("flood_reports").insert(report)
                if (isAdded) {
                    Toast.makeText(requireContext(), "✅ Report submitted successfully!", Toast.LENGTH_LONG).show()
                    capturedBitmap = null; locationDetected = false; mGoogleMap?.clear()
                }
            } catch (e: Exception) {
                Log.e("FloodWatch", "Submit Error: ${e.message}")
                if (isAdded) {
                    AlertDialog.Builder(requireContext()).setTitle("Submission Error").setMessage("Detail: ${e.localizedMessage}").setPositiveButton("OK", null).show()
                }
            } finally {
                progressBar?.visibility = View.GONE; btnSubmit?.isEnabled = true
            }
        }
    }
}