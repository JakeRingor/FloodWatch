package com.example.floodwatch

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.io.File

class CameraCaptureActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var progress: ProgressBar
    private lateinit var captureButton: MaterialButton
    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_capture)

        previewView = findViewById(R.id.cameraPreview)
        progress = findViewById(R.id.cameraProgress)
        captureButton = findViewById(R.id.buttonCapturePhoto)
        captureButton.isEnabled = false

        findViewById<ImageButton>(R.id.buttonCloseCamera).setOnClickListener { finish() }
        captureButton.setOnClickListener { capturePhoto() }
        startCamera()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                progress.visibility = View.GONE
                captureButton.isEnabled = true
            } catch (error: Exception) {
                Toast.makeText(this, "Unable to open camera", Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        // Match the saved JPEG orientation to the current screen orientation.
        capture.targetRotation = previewView.display.rotation
        captureButton.isEnabled = false
        val photo = File.createTempFile("flood_${System.currentTimeMillis()}", ".jpg", cacheDir)
        val options = ImageCapture.OutputFileOptions.Builder(photo).build()

        capture.takePicture(options, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    setResult(
                        Activity.RESULT_OK,
                        Intent().putExtra(EXTRA_PHOTO_PATH, photo.absolutePath)
                    )
                    finish()
                }

                override fun onError(error: ImageCaptureException) {
                    captureButton.isEnabled = true
                    Toast.makeText(this@CameraCaptureActivity, "Photo capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    companion object {
        const val EXTRA_PHOTO_PATH = "captured_photo_path"

        /** BitmapFactory ignores JPEG EXIF orientation, so rotate/flip it explicitly. */
        fun decodeOrientedBitmap(path: String): Bitmap? {
            val bitmap = BitmapFactory.decodeFile(path) ?: return null
            val orientation = try {
                ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        postRotate(90f)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        postRotate(270f)
                        postScale(-1f, 1f)
                    }
                }
            }

            if (orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED
            ) return bitmap

            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { corrected -> if (corrected !== bitmap) bitmap.recycle() }
        }
    }
}
