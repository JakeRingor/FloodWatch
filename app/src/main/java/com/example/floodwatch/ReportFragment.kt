package com.example.floodwatch

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
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
import android.widget.RadioGroup
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
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportFragment : Fragment(), OnMapReadyCallback {

    private var capturedBitmap: Bitmap? = null
    private var analysisJob: Job? = null
    private var analysisText = ""
    private var wheelJob: Job? = null
    private var wheelPreview: Bitmap? = null
    private var wheelText = ""
    private var wheelCount: Int? = null
    private var wheelConfidence: Float? = null
    private var wheelSubmergedFraction: Float? = null
    private var wheelEstimatedDepthCm: Float? = null

    private lateinit var textViewAddress: TextView
    private lateinit var radioGroupFloodLevel: RadioGroup
    private lateinit var textVehiclePassability: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var mGoogleMap: GoogleMap? = null
    private var realtimeChannel: RealtimeChannel? = null
    private var btnAutoDetect: MaterialButton? = null

    private var currentLat = 0.0
    private var currentLng = 0.0
    private var currentElevationMeters: Double? = null
    private var currentElevationAccuracyMeters: Float? = null
    private var currentElevationSource = ElevationSource.UNAVAILABLE
    private var locationDetected = false

    private val elevationApi: ElevationApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ElevationApi::class.java)
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Camera permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val cameraLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val path = result.data
                    ?.getStringExtra(CameraCaptureActivity.EXTRA_PHOTO_PATH)

                val bitmap = path?.let(CameraCaptureActivity::decodeOrientedBitmap)
                path?.let { runCatching { File(it).delete() } }

                if (bitmap != null) {
                    showCapturedPhoto(bitmap)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Could not load photo",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult

            viewLifecycleOwner.lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching { decodeSelectedImage(uri) }.getOrNull()
                }
                if (bitmap != null) {
                    showCapturedPhoto(bitmap)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Could not load selected image",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                getCurrentLocation()
            } else {
                resetAutoDetectButton()
                Toast.makeText(
                    requireContext(),
                    "Location permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(
            R.layout.fragment_report,
            container,
            false
        )

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(requireActivity())

        textViewAddress = view.findViewById(R.id.textViewAddress)
        btnAutoDetect = view.findViewById(R.id.buttonAutoDetect)

        radioGroupFloodLevel =
            view.findViewById(R.id.radioGroupFloodLevel)

        textVehiclePassability =
            view.findViewById(R.id.textVehiclePassability)

        setupFloodParameter()

        view.findViewById<MaterialButton>(R.id.buttonGallery)
            .setOnClickListener {
                detectLocationBeforeCamera()
            }

        view.findViewById<MaterialButton>(R.id.buttonUploadImage)
            .setOnClickListener {
                imagePickerLauncher.launch("image/*")
            }

        view.findViewById<MaterialButton>(R.id.buttonSubmit)
            .setOnClickListener {
                showReportDetailsDialog()
            }

        btnAutoDetect?.setOnClickListener {
            checkLocationPermission()
        }

        return view
    }

    private fun decodeSelectedImage(uri: Uri): Bitmap {
        val resolver = requireContext().contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val largestSide = maxOf(width, height)
                if (largestSide > MAX_SELECTED_IMAGE_SIDE) {
                    val scale = MAX_SELECTED_IMAGE_SIDE.toFloat() / largestSide
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1)
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "Unable to open selected image" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_SELECTED_IMAGE_SIDE) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "Unable to reopen selected image" }
            checkNotNull(BitmapFactory.decodeStream(input, null, options)) {
                "Unsupported image format"
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        renderPhotoPreview()
        capturedBitmap?.let { showCapturedPhoto(it) }

        // The report screen only needs a location preview and a marker. Lite mode
        // renders that much faster on a cold cache than creating a second fully
        // interactive map (the Home screen already owns the full map).
        val mapFragment =
            childFragmentManager.findFragmentByTag(REPORT_MAP_TAG)
                    as? SupportMapFragment
                ?: SupportMapFragment.newInstance(
                    GoogleMapOptions()
                        .liteMode(true)
                        .mapType(GoogleMap.MAP_TYPE_NORMAL)
                        .zoomControlsEnabled(false)
                        .compassEnabled(false)
                ).also { fragment ->
                    childFragmentManager.beginTransaction()
                        .replace(R.id.map, fragment, REPORT_MAP_TAG)
                        .commitNow()
                }

        mapFragment?.getMapAsync(this)
    }

    private fun setupFloodParameter() {
        updateVehiclePassability(selectedFloodDepthRange())

        radioGroupFloodLevel.setOnCheckedChangeListener { _, _ ->
            updateVehiclePassability(selectedFloodDepthRange())
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

    private fun selectedFloodLevel(): String {
        return when (selectedFloodDepthRange()) {
            "16-30 cm" -> "MODERATE"
            "31-50 cm" -> "HIGH"
            "50+ cm" -> "CRITICAL"
            // The database accepts LOW, MODERATE, HIGH, and CRITICAL only.
            // Both passable water (0-5 cm) and shallow water (6-15 cm)
            // therefore belong to the LOW level.
            else -> "LOW"
        }
    }

    private fun selectedSeverity(): Int {
        return when (selectedFloodLevel()) {
            "MODERATE" -> 2
            "HIGH" -> 3
            "CRITICAL" -> 4
            else -> 1
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
        textVehiclePassability.text =
            "$depthRange: ${vehiclePassabilityFor(depthRange)}"
    }

    private fun launchCamera() {
        cameraLauncher.launch(
            android.content.Intent(
                requireContext(),
                CameraCaptureActivity::class.java
            )
        )
    }

    private fun detectLocationBeforeCamera() {
        if (locationDetected) {
            requestCameraPermissionOrLaunch()
        } else {
            Toast.makeText(
                requireContext(),
                "Please tap Auto Detect Location before opening the camera",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestCameraPermissionOrLaunch() {
        if (
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    private fun renderPhotoPreview() {
        val root = view ?: return
        val hasPhoto = capturedBitmap != null
        root.findViewById<ImageView>(R.id.capturedPhotoPreview).apply {
            setImageBitmap(wheelPreview ?: capturedBitmap)
            visibility = if (hasPhoto) View.VISIBLE else View.GONE
        }
        root.findViewById<View>(R.id.capturePlaceholder).visibility = if (hasPhoto) View.GONE else View.VISIBLE
        root.findViewById<TextView>(R.id.captureTitle).text = if (hasPhoto) "Review Your Photo" else "Capture Visual Proof"
        root.findViewById<TextView>(R.id.photoAnalysisResult).apply {
            text = listOf(analysisText, wheelText).filter { it.isNotBlank() }.joinToString("\n\n")
            visibility = if (hasPhoto) View.VISIBLE else View.GONE
        }
        root.findViewById<MaterialButton>(R.id.buttonGallery).text = if (hasPhoto) "Retake Photo" else "Open Camera"
    }

    private fun analyzeWheel() {
        val bitmap = capturedBitmap ?: return
        val appContext = requireContext().applicationContext
        wheelJob?.cancel()
        wheelPreview = null
        wheelText = "Checking for wheels…"
        wheelJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = WheelAnalysis.analyze(appContext, bitmap)
                if (capturedBitmap === bitmap) {
                    wheelPreview = result.preview
                    wheelText = result.description
                    wheelCount = result.wheelCount
                    wheelConfidence = result.highestConfidence
                    wheelSubmergedFraction = result.submergedFraction
                    wheelEstimatedDepthCm = result.estimatedDepthCm
                    result.estimatedDepthCm?.let { depthCm ->
                        applyEstimatedFloodDepth(depthCm)
                        wheelText += "\nFlood Depth Parameter selected automatically. You can change it manually."
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (capturedBitmap === bitmap) {
                    Log.e("FloodWatch", "Offline wheel detection failed", error)
                    wheelText = "Wheel analysis unavailable. Try another photo. You can still report your observations."
                }
            } finally {
                if (capturedBitmap === bitmap && view != null) {
                    wheelJob = null
                    renderPhotoPreview()
                }
            }
        }
        renderPhotoPreview()
    }

    private fun applyEstimatedFloodDepth(depthCm: Float) {
        val radioButtonId = when {
            depthCm <= 5f -> R.id.radioDepthSafe
            depthCm <= 15f -> R.id.radioDepthMotorcycle
            depthCm <= 30f -> R.id.radioDepthSmallCars
            depthCm <= 50f -> R.id.radioDepthLargeVehicles
            else -> R.id.radioDepthClosed
        }
        radioGroupFloodLevel.check(radioButtonId)
    }

    private fun showCapturedPhoto(bitmap: Bitmap) {
        wheelJob?.cancel()
        wheelJob = null
        wheelPreview = null
        wheelText = ""
        wheelCount = null
        wheelConfidence = null
        wheelSubmergedFraction = null
        wheelEstimatedDepthCm = null
        analysisJob?.cancel()
        capturedBitmap = bitmap
        analysisText = "Analyzing photo…"
        analyzeWheel()
        val appContext = requireContext().applicationContext
        analysisJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val score = withContext(Dispatchers.Default) {
                    FloodClassifier.predict(appContext, bitmap)
                }
                if (capturedBitmap === bitmap) {
                    val label = if (score >= FloodClassifier.THRESHOLD) "FLOOD" else "NO FLOOD"
                    analysisText = "Result: $label\nFlood score: %.1f%%\n\nThis estimate can be wrong. Confirm the conditions before reporting.".format(score * 100)
                    renderPhotoPreview()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("FloodWatch", "Photo analysis failed", error)
                if (capturedBitmap === bitmap) {
                    analysisText = "Photo analysis unavailable. You can still submit the conditions you observed."
                    renderPhotoPreview()
                }
            }
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap
        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL

        mGoogleMap?.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(AppLocation.LAT, AppLocation.LNG),
                AppLocation.DEFAULT_ZOOM
            )
        )

        setupRealtime()
    }

    private fun setupRealtime() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                realtimeChannel =
                    SupabaseClient.client.channel("reports-channel")

                val broadcastFlow =
                    realtimeChannel!!.postgresChangeFlow<PostgresAction.Insert>(
                        schema = "public"
                    ) {
                        table = "flood_reports"
                    }

                realtimeChannel!!.subscribe()

                broadcastFlow.collect { action ->
                    val data = action.record

                    val lat =
                        data["latitude"]
                            ?.jsonPrimitive
                            ?.doubleOrNull ?: 0.0

                    val lng =
                        data["longitude"]
                            ?.jsonPrimitive
                            ?.doubleOrNull ?: 0.0

                    val address =
                        data["address"]
                            ?.jsonPrimitive
                            ?.contentOrNull
                            ?: "New Flood Report"

                    activity?.runOnUiThread {
                        mGoogleMap?.addMarker(
                            MarkerOptions()
                                .position(LatLng(lat, lng))
                                .title(address)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    "FloodWatch",
                    "Realtime Error: ${e.message}"
                )
            }
        }
    }

    private fun checkLocationPermission() {
        if (
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        } else {
            requestPermissionLauncher.launch(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    private fun getCurrentLocation() {
        btnAutoDetect?.isEnabled = false
        btnAutoDetect?.text = "Detecting…"

        val cancellationTokenSource =
            CancellationTokenSource()

        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    val hasMslElevation =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                                location.hasMslAltitude()

                    val elevation = when {
                        hasMslElevation -> location.mslAltitudeMeters
                        location.hasAltitude() -> location.altitude
                        else -> null
                    }

                    onLocationReceived(
                        location.latitude,
                        location.longitude,
                        elevation,
                        if (location.hasVerticalAccuracy()) {
                            location.verticalAccuracyMeters
                        } else {
                            null
                        },
                        hasMslElevation
                    )
                } else {
                    resetAutoDetectButton()
                }
            }.addOnFailureListener {
                resetAutoDetectButton()
            }
        } catch (e: SecurityException) {
            resetAutoDetectButton()
        }
    }

    private fun onLocationReceived(
        lat: Double,
        lng: Double,
        elevationMeters: Double?,
        elevationAccuracyMeters: Float?,
        elevationIsMsl: Boolean
    ) {
        currentLat = lat
        currentLng = lng
        currentElevationMeters = elevationMeters
        currentElevationAccuracyMeters = elevationAccuracyMeters
        currentElevationSource = when {
            elevationMeters == null -> ElevationSource.UNAVAILABLE
            elevationIsMsl -> ElevationSource.MSL
            else -> ElevationSource.GPS
        }
        locationDetected = true

        fetchTerrainElevation(lat, lng)

        val userLatLng = LatLng(lat, lng)

        mGoogleMap?.clear()
        mGoogleMap?.addMarker(
            MarkerOptions()
                .position(userLatLng)
                .title("Your Location")
        )

        mGoogleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                userLatLng,
                17f
            )
        )

        btnAutoDetect?.isEnabled = true
        btnAutoDetect?.text = "Re-detect Location"

        val geocoder =
            Geocoder(requireContext(), Locale.getDefault())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(lat, lng, 1) { addresses ->
                activity?.runOnUiThread {
                    textViewAddress.text =
                        if (addresses.isNotEmpty()) {
                            addresses[0].getAddressLine(0)
                        } else {
                            "Lat: %.5f, Lng: %.5f".format(lat, lng)
                        }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val addresses =
                geocoder.getFromLocation(lat, lng, 1)

            textViewAddress.text =
                if (!addresses.isNullOrEmpty()) {
                    addresses[0].getAddressLine(0)
                } else {
                    "Lat: %.5f, Lng: %.5f".format(lat, lng)
                }
        }
    }

    private fun resetAutoDetectButton() {
        btnAutoDetect?.isEnabled = true
        btnAutoDetect?.text = "Auto Detect Location"
    }

    private fun fetchTerrainElevation(lat: Double, lng: Double) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val terrainElevation = elevationApi
                    .getElevation(lat, lng)
                    .elevation
                    .firstOrNull()

                // Ignore a late result if the user has already detected another point.
                if (
                    terrainElevation != null &&
                    currentLat == lat &&
                    currentLng == lng
                ) {
                    currentElevationMeters = terrainElevation
                    currentElevationAccuracyMeters = null
                    currentElevationSource = ElevationSource.TERRAIN_DEM
                }
            } catch (error: Exception) {
                // Keep the GPS/MSL reading as an offline fallback.
                Log.w(
                    "FloodWatch",
                    "Terrain elevation unavailable: ${error.message}"
                )
            }
        }
    }

    private fun showReportDetailsDialog() {
        if (capturedBitmap == null || !locationDetected) {
            Toast.makeText(
                requireContext(),
                "Please capture a photo and detect location first",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val depthRange = selectedFloodDepthRange()
        val floodLevel = selectedFloodLevel()
        val severity = selectedSeverity()
        val vehicleGuidance =
            vehiclePassabilityFor(depthRange)
        val wheelDepthLine = wheelEstimatedDepthCm
            ?.let { "\nWheel-estimated depth: %.1f cm".format(Locale.US, it) }
            .orEmpty()

        MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_FloodWatch_MaterialAlertDialog
        )
            .setTitle("Confirm Flood Report")
            .setMessage(
                "Flood depth: $depthRange\n" +
                        "Flood level: $floodLevel\n" +
                        "Vehicle guidance: $vehicleGuidance" + wheelDepthLine + "\n" +
                        formattedElevation()
            )
            .setPositiveButton("Submit") { _, _ ->
                submitReport(
                    level = floodLevel,
                    passability = vehicleGuidance,
                    depthRange = depthRange,
                    severity = severity
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addOverlayToBitmap(
        bitmap: Bitmap,
        address: String
    ): Bitmap {
        val result =
            bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(result)

        val paint = Paint().apply {
            color = Color.WHITE
            textSize = maxOf(24f, bitmap.width / 38f)
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
            isAntiAlias = true
            setShadowLayer(
                3f,
                2f,
                2f,
                Color.BLACK
            )
        }

        val timestamp =
            SimpleDateFormat(
                "MMM dd, yyyy hh:mm a",
                Locale.getDefault()
            ).format(Date())

        val coordinates =
            "Coordinates: %.5f, %.5f".format(
                Locale.US,
                currentLat,
                currentLng
            )
        val lines = buildList {
            add(serviceAreaLabel())
            add(address)
            add(coordinates)
            add(formattedElevation())
            wheelEstimatedDepthCm?.let {
                add("Wheel-estimated flood depth: %.1f cm".format(Locale.US, it))
            }
            add(timestamp)
        }
        val horizontalPadding = maxOf(24f, result.width * 0.035f)
        val verticalPadding = maxOf(20f, result.height * 0.025f)
        val lineSpacing = paint.textSize * 1.3f
        val panelHeight = verticalPadding * 2 + lineSpacing * lines.size

        val backgroundPaint = Paint().apply {
            color = Color.argb(170, 0, 0, 0)
        }
        canvas.drawRect(
            0f,
            result.height - panelHeight,
            result.width.toFloat(),
            result.height.toFloat(),
            backgroundPaint
        )

        var baseline = result.height - panelHeight + verticalPadding + paint.textSize
        val maximumTextWidth = result.width - (horizontalPadding * 2)
        lines.forEach { line ->
            canvas.drawText(
                fitOverlayText(line, paint, maximumTextWidth),
                horizontalPadding,
                baseline,
                paint
            )
            baseline += lineSpacing
        }

        return result
    }

    private fun serviceAreaLabel(): String =
        "Service area: ${AppLocation.BARANGAY}, ${AppLocation.MUNICIPALITY}"

    private fun formattedElevation(): String {
        val elevation = currentElevationMeters ?: return "Elevation: unavailable"
        val reference = when (currentElevationSource) {
            ElevationSource.TERRAIN_DEM -> "terrain"
            ElevationSource.MSL -> "MSL"
            ElevationSource.GPS -> "GPS approx."
            ElevationSource.UNAVAILABLE -> ""
        }
        val accuracy = currentElevationAccuracyMeters
            ?.let { " ±%.0f m".format(Locale.US, it) }
            .orEmpty()
        return "Elevation: %.1f m $reference%s".format(
            Locale.US,
            elevation,
            accuracy
        )
    }

    private fun fitOverlayText(
        text: String,
        paint: Paint,
        maximumWidth: Float
    ): String {
        if (paint.measureText(text) <= maximumWidth) return text
        val ellipsis = "…"
        val availableWidth = maximumWidth - paint.measureText(ellipsis)
        val characterCount = paint.breakText(text, true, availableWidth, null)
        return text.take(characterCount.coerceAtLeast(0)) + ellipsis
    }

    private fun submitReport(
        level: String,
        passability: String,
        depthRange: String,
        severity: Int
    ) {
        val currentUserId =
            SupabaseClient.client.auth
                .currentUserOrNull()
                ?.id
                ?: return

        val progressBar =
            view?.findViewById<ProgressBar>(
                R.id.progressBar
            )

        val btnSubmit =
            view?.findViewById<MaterialButton>(
                R.id.buttonSubmit
            )

        viewLifecycleOwner.lifecycleScope.launch {
            var uploadedFileName: String? = null
            try {
                progressBar?.visibility = View.VISIBLE
                btnSubmit?.isEnabled = false

                val addressStr =
                    textViewAddress.text.toString()

                val processedBitmap =
                    addOverlayToBitmap(
                        capturedBitmap!!,
                        addressStr
                    )

                val outputStream =
                    ByteArrayOutputStream()

                processedBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    90,
                    outputStream
                )

                val fileName =
                    "$currentUserId/report_${System.currentTimeMillis()}.jpg"

                val bucket =
                    SupabaseClient.client.storage
                        .from("flood-reports")

                bucket.upload(
                    path = fileName,
                    data = outputStream.toByteArray()
                )
                uploadedFileName = fileName

                val imageUrl =
                    bucket.publicUrl(fileName)

                val vehicleGuidance =
                    vehiclePassabilityFor(depthRange)

                val report = FloodReport(
                    userId = currentUserId,
                    imageUrl = imageUrl,
                    address = addressStr,
                    latitude = currentLat,
                    longitude = currentLng,
                    floodLevel = level,
                    passability = passability,
                    status = "pending",
                    severity = severity,
                    wheelCount = wheelCount,
                    wheelConfidence = wheelConfidence?.toDouble(),
                    wheelSubmergedPercent = wheelSubmergedFraction?.times(100)?.toDouble(),
                    wheelEstimatedDepthCm = wheelEstimatedDepthCm?.toDouble(),
                    description =
                        "Flood depth: $depthRange. " +
                                "Vehicle guidance: $vehicleGuidance. " +
                                (wheelEstimatedDepthCm?.let {
                                    "Wheel-estimated depth: %.1f cm. ".format(Locale.US, it)
                                } ?: "") +
                                formattedElevation()
                )

                SupabaseClient.client
                    .from("flood_reports")
                    .insert(report)
                // The database now owns the Storage object. Do not delete it on
                // any later UI error in this coroutine.
                uploadedFileName = null

                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "✅ Report submitted successfully!",
                        Toast.LENGTH_LONG
                    ).show()

                    capturedBitmap = null
                    wheelJob?.cancel()
                    wheelJob = null
                    wheelPreview = null
                    wheelText = ""
                    wheelCount = null
                    wheelConfidence = null
                    wheelSubmergedFraction = null
                    wheelEstimatedDepthCm = null
                    analysisJob?.cancel()
                    analysisText = ""
                    renderPhotoPreview()
                    locationDetected = false
                    currentElevationMeters = null
                    currentElevationAccuracyMeters = null
                    currentElevationSource = ElevationSource.UNAVAILABLE
                    mGoogleMap?.clear()

                    textViewAddress.text =
                        "Detecting location..."

                    resetAutoDetectButton()
                }
            } catch (e: CancellationException) {
                cleanupFailedReportUpload(uploadedFileName)
                throw e
            } catch (e: Exception) {
                cleanupFailedReportUpload(uploadedFileName)
                Log.e(
                    "FloodWatch",
                    "Submit Error: ${e.message}"
                )

                if (isAdded) {
                    MaterialAlertDialogBuilder(
                        requireContext(),
                        R.style.ThemeOverlay_FloodWatch_MaterialAlertDialog
                    )
                        .setTitle("Submission Error")
                        .setMessage(
                            "Detail: ${e.localizedMessage}"
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
            } finally {
                progressBar?.visibility = View.GONE
                btnSubmit?.isEnabled = true
            }
        }
    }

    private suspend fun cleanupFailedReportUpload(path: String?) {
        if (path == null) return
        withContext(NonCancellable) {
            runCatching {
                SupabaseClient.client.storage.from("flood-reports").delete(path)
            }.onFailure {
                Log.w("FloodWatch", "Could not remove failed report upload: $path", it)
            }
        }
    }

    override fun onDestroyView() {
        wheelJob?.cancel()
        wheelJob = null
        analysisJob?.cancel()
        analysisJob = null
        val channel = realtimeChannel
        realtimeChannel = null
        lifecycleScope.launch {
            try {
                channel?.unsubscribe()
            } catch (e: Exception) {
                Log.e(
                    "FloodWatch",
                    "Unsubscribe error: ${e.message}"
                )
            }
        }
        super.onDestroyView()
    }

    private enum class ElevationSource {
        UNAVAILABLE,
        TERRAIN_DEM,
        MSL,
        GPS
    }

    companion object {
        private const val REPORT_MAP_TAG = "report_location_map"
        private const val MAX_SELECTED_IMAGE_SIDE = 2048
    }
}
